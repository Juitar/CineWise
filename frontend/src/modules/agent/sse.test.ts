import { afterEach, describe, expect, it, vi } from 'vitest';

import processingEvent from '../../../../backend/src/test/resources/fixtures/agent/c/processing-event.json';
import { clearCsrfToken } from '../../shared/api/client';
import { AgentContractError } from './contract';
import { parseSseStream, postAgentStream } from './sse';

function streamFrom(chunks: readonly string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

function csrfResponse(token = 'csrf-value'): Response {
  return new Response(
    JSON.stringify({
      code: 0,
      message: 'success',
      traceId: 'trace-csrf',
      data: { headerName: 'X-XSRF-TOKEN', token },
    }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  );
}

const REQUEST = {
  clientRequestId: '4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925',
  content: '推荐电影',
  context: { entry: 'workspace' },
};

afterEach(() => {
  clearCsrfToken();
  vi.unstubAllGlobals();
});

describe('SSE 分块解析', () => {
  it('支持一个事件跨多个分块和多个事件同一分块', async () => {
    const messages: string[] = [];
    await parseSseStream(
      streamFrom([
        'id: 1\nevent: text\ndata: {"a":',
        '1}\n\nid: 2\ndata: second\n\nid: 3\ndata: third\n\n',
      ]),
      (message) => {
        messages.push(`${message.id}:${message.data}`);
      },
      vi.fn(),
    );
    expect(messages).toEqual(['1:{"a":1}', '2:second', '3:third']);
  });

  it('把无 ID 注释视为心跳', async () => {
    const heartbeat = vi.fn();
    const message = vi.fn();
    await parseSseStream(streamFrom([':heartbeat\r\n\r\n']), message, heartbeat);
    expect(heartbeat).toHaveBeenCalledOnce();
    expect(message).not.toHaveBeenCalled();
  });
});

describe('POST SSE 客户端', () => {
  it('携带 JSON、Cookie、CSRF 和 Last-Event-ID 并解析事件', async () => {
    const eventText = `id: 40\nevent: step.start\ndata: ${JSON.stringify(processingEvent)}\n\n`;
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(new Response(streamFrom([eventText]), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const onEvent = vi.fn();
    await postAgentStream('session-example-1', REQUEST, '39', new AbortController().signal, {
      onEvent,
      onHeartbeat: vi.fn(),
    });
    const [, options] = fetchMock.mock.calls[1];
    expect(options.credentials).toBe('include');
    expect(options.body).toBe(JSON.stringify(REQUEST));
    expect((options.headers as Headers).get('X-XSRF-TOKEN')).toBe('csrf-value');
    expect((options.headers as Headers).get('Last-Event-ID')).toBe('39');
    expect(onEvent).toHaveBeenCalledWith(expect.objectContaining({ eventId: '40' }));
  });

  it('每次 POST SSE 后清除旧 CSRF，断线重连重新获取当前 Token', async () => {
    const secondCsrfResponse = new Response(
      JSON.stringify({
        code: 0,
        message: 'success',
        traceId: 'trace-csrf-2',
        data: { headerName: 'X-XSRF-TOKEN', token: 'csrf-value-2' },
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    );
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(new Response(streamFrom([]), { status: 200 }))
      .mockResolvedValueOnce(secondCsrfResponse)
      .mockResolvedValueOnce(new Response(streamFrom([]), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await postAgentStream('session-example-1', REQUEST, null, new AbortController().signal, {
      onEvent: vi.fn(),
      onHeartbeat: vi.fn(),
    });
    await postAgentStream('session-example-1', REQUEST, '40', new AbortController().signal, {
      onEvent: vi.fn(),
      onHeartbeat: vi.fn(),
    });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/csrf');
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/auth/csrf');
    expect((fetchMock.mock.calls[3][1].headers as Headers).get('X-XSRF-TOKEN')).toBe(
      'csrf-value-2',
    );
  });

  it.each([
    [201007, false],
    [201009, true],
  ])('SSE 建连返回 403/%s 时按公共规则处理 CSRF Token', async (code, shouldRefresh) => {
    const responses = [
      csrfResponse(),
      new Response(JSON.stringify({ code, message: 'denied', data: null }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
      ...(shouldRefresh ? [csrfResponse()] : []),
      new Response(streamFrom([]), { status: 200 }),
    ];
    const fetchMock = vi.fn().mockImplementation(async () => responses.shift());
    vi.stubGlobal('fetch', fetchMock);

    await expect(
      postAgentStream('session-example-1', REQUEST, null, new AbortController().signal, {
        onEvent: vi.fn(),
        onHeartbeat: vi.fn(),
      }),
    ).rejects.toMatchObject({ status: 403, code });
    await postAgentStream('session-example-1', REQUEST, '40', new AbortController().signal, {
      onEvent: vi.fn(),
      onHeartbeat: vi.fn(),
    });

    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/v1/auth/csrf')).toHaveLength(
      shouldRefresh ? 2 : 1,
    );
  });

  it('网络断开后恢复连接会重新获取 CSRF Token', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('csrf-value-1'))
      .mockRejectedValueOnce(new TypeError('network down'))
      .mockResolvedValueOnce(csrfResponse('csrf-value-2'))
      .mockResolvedValueOnce(new Response(streamFrom([]), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(
      postAgentStream('session-example-1', REQUEST, null, new AbortController().signal, {
        onEvent: vi.fn(),
        onHeartbeat: vi.fn(),
      }),
    ).rejects.toMatchObject({ kind: 'NETWORK' });
    await postAgentStream('session-example-1', REQUEST, '40', new AbortController().signal, {
      onEvent: vi.fn(),
      onHeartbeat: vi.fn(),
    });

    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/v1/auth/csrf')).toHaveLength(2);
    expect((fetchMock.mock.calls[3][1].headers as Headers).get('X-XSRF-TOKEN')).toBe(
      'csrf-value-2',
    );
  });

  it('非法 JSON 不会作为事件投递', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(
          new Response(streamFrom(['id: 1\ndata: {bad}\n\n']), { status: 200 }),
        ),
    );
    await expect(
      postAgentStream('session-example-1', REQUEST, null, new AbortController().signal, {
        onEvent: vi.fn(),
        onHeartbeat: vi.fn(),
      }),
    ).rejects.toBeInstanceOf(AgentContractError);
  });

  it('HTTP 错误转为安全 ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({ code: 206008, message: 'running', data: null, traceId: 'trace' }),
            { status: 409, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
    );
    await expect(
      postAgentStream('session-example-1', REQUEST, null, new AbortController().signal, {
        onEvent: vi.fn(),
        onHeartbeat: vi.fn(),
      }),
    ).rejects.toMatchObject({ status: 409, code: 206008 });
  });

  it('取消和卸载通过 AbortSignal 终止请求', async () => {
    const controller = new AbortController();
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockImplementationOnce(async () => {
          controller.abort();
          throw new DOMException('aborted', 'AbortError');
        }),
    );
    await expect(
      postAgentStream('session-example-1', REQUEST, null, controller.signal, {
        onEvent: vi.fn(),
        onHeartbeat: vi.fn(),
      }),
    ).rejects.toMatchObject({ kind: 'CANCELLED' });
  });
});
