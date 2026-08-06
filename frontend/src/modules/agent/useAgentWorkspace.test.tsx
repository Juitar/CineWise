import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import processingEventFixture from '../../../../backend/src/test/resources/fixtures/agent/c/processing-event.json';
import { ApiError } from '../../shared/api/ApiError';
import { notifySessionEnding } from '../../shared/auth/sessionLifecycle';
import { parseAgentEvent } from './contract';
import type { AgentRunSnapshot } from './types';

const mocks = vi.hoisted(() => ({
  cancelAgentRun: vi.fn(),
  clearAgentSession: vi.fn(),
  clearAllAgentSessions: vi.fn(),
  createAgentSession: vi.fn(),
  getAgentRun: vi.fn(),
  listAgentMessages: vi.fn(),
  listAgentSessions: vi.fn(),
  postAgentStream: vi.fn(),
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => ({ status: 'authenticated' }),
}));

vi.mock('./api', () => ({
  cancelAgentRun: mocks.cancelAgentRun,
  clearAgentSession: mocks.clearAgentSession,
  clearAllAgentSessions: mocks.clearAllAgentSessions,
  createAgentSession: mocks.createAgentSession,
  getAgentRun: mocks.getAgentRun,
  listAgentMessages: mocks.listAgentMessages,
  listAgentSessions: mocks.listAgentSessions,
}));

vi.mock('./sse', () => ({ postAgentStream: mocks.postAgentStream }));

import { useAgentSessionBootstrap, useAgentWorkspace } from './useAgentWorkspace';

const processingEvent = parseAgentEvent(processingEventFixture);
const emptyMessages = { total: 0, page: 1, size: 100, records: [] };
const sessionPage = {
  total: 1,
  page: 1,
  size: 100,
  records: [
    {
      sessionId: 'session-example-1',
      summary: '测试会话',
      status: 'ACTIVE',
      createdAt: '2026-08-05T10:00:00+08:00',
      updatedAt: '2026-08-05T10:00:00+08:00',
    },
  ],
};

function snapshot(status: AgentRunSnapshot['status']): AgentRunSnapshot {
  return {
    runId: 'run-example-1',
    sessionId: 'session-example-1',
    status,
    planId: null,
    planVersion: 1,
    startedAt: null,
    finishedAt: status === 'RUNNING' ? null : '2026-08-05T10:00:01+08:00',
    lastEventId: '40',
    messages: [],
    steps: [],
    events: [],
  };
}

beforeEach(() => {
  mocks.listAgentMessages.mockResolvedValue(emptyMessages);
  mocks.listAgentSessions.mockResolvedValue(sessionPage);
  mocks.getAgentRun.mockResolvedValue(snapshot('COMPLETED'));
});

afterEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe('useAgentWorkspace 状态与恢复', () => {
  it.each([
    [401, 201006, '登录状态已失效'],
    [403, undefined, '没有权限'],
    [404, 206005, '会话不存在'],
    [409, 206008, '会话仍在运行'],
  ])('加载错误 %s 显示固定安全提示', async (status, code, expected) => {
    mocks.listAgentMessages.mockRejectedValue(
      new ApiError('raw backend message', { kind: 'HTTP', status, code }),
    );
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('error'));
    expect(result.current.feedback).toContain(expected);
    expect(result.current.feedback).not.toContain('raw backend message');
  });

  it('网络错误保留固定页面提示', async () => {
    mocks.listAgentMessages.mockRejectedValue(new ApiError('raw', { kind: 'NETWORK' }));
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('error'));
    expect(result.current.feedback).toBe('网络连接失败，已保留当前内容');
  });

  it('新工作区只创建一次会话', async () => {
    mocks.createAgentSession.mockResolvedValue(sessionPage.records[0]);
    const { result } = renderHook(() => useAgentSessionBootstrap(true));
    await waitFor(() => expect(result.current.createdSessionId).toBe('session-example-1'));
    expect(mocks.createAgentSession).toHaveBeenCalledOnce();
  });

  it('首个事件前断线进入 RESULT_UNKNOWN，且不自动重发', async () => {
    mocks.postAgentStream.mockRejectedValue(new ApiError('network', { kind: 'NETWORK' }));
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    await act(async () => {
      await result.current.submit('推荐电影');
    });
    expect(result.current.projection.status).toBe('RESULT_UNKNOWN');
    expect(mocks.postAgentStream).toHaveBeenCalledOnce();
    expect(mocks.getAgentRun).not.toHaveBeenCalled();
  });

  it('首事件前收到 409 明确拒绝时进入失败而不是结果未知', async () => {
    mocks.postAgentStream.mockRejectedValue(
      new ApiError('running', { kind: 'HTTP', status: 409, code: 206008 }),
    );
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    await act(async () => {
      await result.current.submit('推荐电影');
    });
    expect(result.current.projection.status).toBe('FAILED');
    expect(result.current.projection.safeError).toContain('会话仍在运行');
  });

  it('退出登录开始时立即中止活动 SSE', async () => {
    const signalHolder: { current?: AbortSignal } = {};
    mocks.postAgentStream.mockImplementation(async (_session, _request, _cursor, signal) => {
      signalHolder.current = signal;
      await new Promise<void>((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')), {
          once: true,
        });
      });
    });
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    const submission = result.current.submit('推荐电影');
    await waitFor(() => expect(signalHolder.current).toBeDefined());
    act(() => notifySessionEnding());
    expect(signalHolder.current?.aborted).toBe(true);
    await act(async () => {
      await submission;
    });
  });

  it('活动请求期间拒绝重复提交', async () => {
    let finish!: () => void;
    mocks.postAgentStream.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve;
        }),
    );
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    let first!: Promise<boolean>;
    act(() => {
      first = result.current.submit('第一次');
    });
    await waitFor(() => expect(result.current.projection.status).toBe('CONNECTING'));
    await expect(result.current.submit('第二次')).resolves.toBe(false);
    expect(mocks.postAgentStream).toHaveBeenCalledOnce();
    finish();
    await act(async () => {
      await first;
    });
  });

  it('已有 runId 断线时先查询原运行并按快照完成', async () => {
    mocks.postAgentStream.mockImplementation(
      async (_session, _request, _cursor, _signal, handlers) => {
        await handlers.onEvent(processingEvent);
        throw new ApiError('network', { kind: 'NETWORK' });
      },
    );
    mocks.getAgentRun.mockResolvedValue(snapshot('COMPLETED'));
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    await act(async () => {
      await result.current.submit('推荐电影');
    });
    expect(mocks.getAgentRun).toHaveBeenCalledWith('run-example-1');
    expect(result.current.projection.status).toBe('COMPLETED');
  });

  it('主动取消使用服务端返回的终态', async () => {
    mocks.postAgentStream.mockImplementation(
      async (_session, _request, _cursor, _signal, handlers) => {
        await handlers.onEvent(processingEvent);
        await new Promise<void>(() => {
          // 保持活动连接，直到测试调用取消。
        });
      },
    );
    mocks.cancelAgentRun.mockResolvedValue({
      runId: 'run-example-1',
      status: 'CANCELLED',
      finishedAt: '2026-08-05T10:00:01+08:00',
    });
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    act(() => {
      void result.current.submit('推荐电影');
    });
    await waitFor(() => expect(result.current.projection.runId).toBe('run-example-1'));
    await act(async () => {
      await result.current.cancel();
    });
    expect(result.current.projection.status).toBe('CANCELLED');
    expect(mocks.cancelAgentRun).toHaveBeenCalledWith('run-example-1');
  });

  it('20 秒无事件或心跳时查询原运行并使用原游标续传', async () => {
    vi.useFakeTimers();
    mocks.getAgentRun.mockResolvedValue(snapshot('RUNNING'));
    mocks.postAgentStream.mockImplementation(
      async (_session, _request, _cursor, signal, handlers) => {
        if (mocks.postAgentStream.mock.calls.length === 1) await handlers.onEvent(processingEvent);
        await new Promise<void>((_resolve, reject) => {
          signal.addEventListener(
            'abort',
            () => reject(new DOMException('aborted', 'AbortError')),
            {
              once: true,
            },
          );
        });
      },
    );
    const { result, unmount } = renderHook(() => useAgentWorkspace('session-example-1'));
    await act(async () => {
      await Promise.resolve();
    });
    const submission = result.current.submit('推荐电影');
    await act(async () => {
      await Promise.resolve();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(20_000);
    });
    expect(mocks.getAgentRun).toHaveBeenCalledWith('run-example-1');
    expect(mocks.postAgentStream).toHaveBeenCalledTimes(2);
    expect(mocks.postAgentStream.mock.calls[1][2]).toBe('40');
    unmount();
    await act(async () => {
      await submission;
    });
  });
});
