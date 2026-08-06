import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { apiRequest, clearCsrfToken, setUnauthorizedHandler } from './client';

function createApiResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

function createRawResponse(body: BodyInit | null, status = 500): Response {
  return new Response(body, {
    status,
    headers: {
      'Content-Type': 'text/plain',
      'X-Trace-Id': 'trace-from-header',
    },
  });
}

describe('apiRequest', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    clearCsrfToken();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('编码查询参数、携带 Cookie 并返回 data', async () => {
    fetchMock.mockResolvedValue(
      createApiResponse({
        code: 0,
        message: 'success',
        data: { movieId: '1001' },
        traceId: 'trace-1',
      }),
    );

    const movie = await apiRequest<{ movieId: string }>('/api/v1/movies/current', {
      query: {
        cinemaId: '2001',
        status: ['ON_SALE', 'PRESALE'],
      },
    });

    expect(movie).toEqual({ movieId: '1001' });
    const call = fetchMock.mock.calls.at(0);
    if (!call) {
      throw new Error('fetch 未被调用');
    }

    const [url, requestInit] = call;
    expect(url).toBe('/api/v1/movies/current?cinemaId=2001&status=ON_SALE&status=PRESALE');
    expect(requestInit).toMatchObject({
      credentials: 'include',
      method: 'GET',
    });
  });

  it('业务码不为 0 时抛出结构化错误', async () => {
    fetchMock.mockResolvedValue(
      createApiResponse({
        code: 200101,
        message: 'show not on sale',
        traceId: 'trace-2',
      }),
    );

    await expect(apiRequest('/api/v1/shows/3001')).rejects.toMatchObject({
      kind: 'BUSINESS',
      code: 200101,
      message: 'show not on sale',
      status: 200,
      traceId: 'trace-2',
      isResultUnknown: false,
    });
  });

  it('写请求超时后标记结果未知，避免调用方重复提交', async () => {
    vi.useFakeTimers();
    fetchMock.mockResolvedValueOnce(
      createApiResponse({
        code: 0,
        message: 'success',
        data: { token: 'csrf-timeout', headerName: 'X-XSRF-TOKEN' },
        traceId: 'trace-csrf-timeout',
      }),
    );
    fetchMock.mockImplementationOnce(
      (_input, requestInit) =>
        new Promise((_resolve, reject) => {
          requestInit?.signal?.addEventListener('abort', () => {
            reject(new DOMException('Aborted', 'AbortError'));
          });
        }),
    );

    const requestPromise = apiRequest('/api/v1/orders', {
      method: 'POST',
      body: {
        clientRequestId: 'request-1',
        seatIds: ['seat-1'],
      },
      timeoutMs: 50,
    });
    const expectation = expect(requestPromise).rejects.toMatchObject({
      kind: 'TIMEOUT',
      isResultUnknown: true,
    });

    await Promise.resolve();
    await Promise.resolve();
    await vi.advanceTimersByTimeAsync(50);
    await expectation;
  });

  it('并发写请求只获取一次 CSRF Token，并统一添加服务端指定的 Header', async () => {
    fetchMock
      .mockResolvedValueOnce(
        createApiResponse({
          code: 0,
          message: 'success',
          data: { token: 'csrf-1', headerName: 'X-XSRF-TOKEN' },
          traceId: 'trace-csrf',
        }),
      )
      .mockImplementation(async () =>
        createApiResponse({
          code: 0,
          message: 'success',
          data: { accepted: true },
          traceId: 'trace-write',
        }),
      );

    await Promise.all([
      apiRequest('/api/v1/orders/1/cancel', { method: 'POST' }),
      apiRequest('/api/v1/orders/2/cancel', { method: 'POST' }),
    ]);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/v1/auth/csrf')).toHaveLength(1);
    fetchMock.mock.calls.slice(1).forEach(([, requestInit]) => {
      expect(new Headers(requestInit?.headers).get('X-XSRF-TOKEN')).toBe('csrf-1');
    });
  });

  it('连续写请求在收到响应后重新获取服务端更新的 CSRF Token', async () => {
    fetchMock
      .mockResolvedValueOnce(
        createApiResponse({
          code: 0,
          message: 'success',
          data: { token: 'csrf-first', headerName: 'X-XSRF-TOKEN' },
          traceId: 'trace-csrf-first',
        }),
      )
      .mockResolvedValueOnce(
        createApiResponse({ code: 0, message: 'success', data: {}, traceId: 'trace-write-first' }),
      )
      .mockResolvedValueOnce(
        createApiResponse({
          code: 0,
          message: 'success',
          data: { token: 'csrf-second', headerName: 'X-XSRF-TOKEN' },
          traceId: 'trace-csrf-second',
        }),
      )
      .mockResolvedValueOnce(
        createApiResponse({ code: 0, message: 'success', data: {}, traceId: 'trace-write-second' }),
      );

    await apiRequest('/api/v1/orders', { method: 'POST' });
    await apiRequest('/api/v1/orders/1/payments', { method: 'POST' });

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/v1/auth/csrf',
      '/api/v1/orders',
      '/api/v1/auth/csrf',
      '/api/v1/orders/1/payments',
    ]);
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'csrf-first',
    );
    expect(new Headers(fetchMock.mock.calls[3]?.[1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'csrf-second',
    );
  });

  it('收到 201009 后换新 CSRF Token，但不自动重放原写请求', async () => {
    fetchMock
      .mockResolvedValueOnce(
        createApiResponse({
          code: 0,
          message: 'success',
          data: { token: 'csrf-old', headerName: 'X-XSRF-TOKEN' },
          traceId: 'trace-csrf-old',
        }),
      )
      .mockResolvedValueOnce(
        createApiResponse(
          {
            code: 201009,
            message: 'csrf invalid',
            data: null,
            traceId: 'trace-csrf-invalid',
          },
          403,
        ),
      )
      .mockResolvedValueOnce(
        createApiResponse({
          code: 0,
          message: 'success',
          data: { token: 'csrf-new', headerName: 'X-XSRF-TOKEN' },
          traceId: 'trace-csrf-new',
        }),
      );

    await expect(apiRequest('/api/v1/auth/logout', { method: 'POST' })).rejects.toMatchObject({
      kind: 'HTTP',
      code: 201009,
      status: 403,
    });

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/v1/auth/csrf',
      '/api/v1/auth/logout',
      '/api/v1/auth/csrf',
    ]);
  });

  it('403/201009 省略 data 时换新 CSRF Token，但不重放原写请求', async () => {
    fetchMock
      .mockResolvedValueOnce(
        createApiResponse({
          code: 0,
          message: 'success',
          data: { token: 'csrf-old', headerName: 'X-XSRF-TOKEN' },
          traceId: 'trace-csrf-old',
        }),
      )
      .mockResolvedValueOnce(
        createApiResponse(
          {
            code: 201009,
            message: '安全校验已失效，请重新操作',
            traceId: 'trace-csrf-invalid',
          },
          403,
        ),
      )
      .mockResolvedValueOnce(
        createApiResponse({
          code: 0,
          message: 'success',
          data: { token: 'csrf-new', headerName: 'X-XSRF-TOKEN' },
          traceId: 'trace-csrf-new',
        }),
      );

    await expect(apiRequest('/api/v1/orders/refund', { method: 'POST' })).rejects.toMatchObject({
      kind: 'HTTP',
      code: 201009,
      message: '安全校验已失效，请重新操作',
      status: 403,
      traceId: 'trace-csrf-invalid',
    });
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/v1/auth/csrf',
      '/api/v1/orders/refund',
      '/api/v1/auth/csrf',
    ]);
  });

  it('403/201007 省略 data 时保留登录状态和当前 CSRF Token', async () => {
    const handleUnauthorized = vi.fn();
    const removeHandler = setUnauthorizedHandler(handleUnauthorized);
    fetchMock
      .mockResolvedValueOnce(
        createApiResponse({
          code: 0,
          message: 'success',
          data: { token: 'csrf-current', headerName: 'X-XSRF-TOKEN' },
          traceId: 'trace-csrf-current',
        }),
      )
      .mockResolvedValueOnce(
        createApiResponse({ code: 201007, message: '权限不足', traceId: 'trace-forbidden' }, 403),
      )
      .mockResolvedValueOnce(
        createApiResponse({ code: 0, message: 'success', data: {}, traceId: 'trace-next-write' }),
      );

    await expect(apiRequest('/api/v1/admin/orders', { method: 'POST' })).rejects.toMatchObject({
      code: 201007,
      status: 403,
      traceId: 'trace-forbidden',
    });
    await expect(apiRequest('/api/v1/orders', { method: 'POST' })).resolves.toEqual({});

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/v1/auth/csrf',
      '/api/v1/admin/orders',
      '/api/v1/orders',
    ]);
    expect(handleUnauthorized).not.toHaveBeenCalled();
    removeHandler();
  });

  it('401/201006 省略 data 时先执行会话失效处理再保留真实错误', async () => {
    const handleUnauthorized = vi.fn();
    const removeHandler = setUnauthorizedHandler(handleUnauthorized);
    fetchMock.mockResolvedValue(
      createApiResponse(
        { code: 201006, message: '登录状态已失效', traceId: 'trace-401-no-data' },
        401,
      ),
    );

    await expect(apiRequest('/api/v1/auth/me')).rejects.toMatchObject({
      kind: 'HTTP',
      code: 201006,
      message: '登录状态已失效',
      status: 401,
      traceId: 'trace-401-no-data',
    });
    expect(handleUnauthorized).toHaveBeenCalledOnce();
    removeHandler();
  });

  it('并发 401 只执行一次全局会话清理', async () => {
    const handleUnauthorized = vi.fn(async () => {
      await Promise.resolve();
    });
    const removeHandler = setUnauthorizedHandler(handleUnauthorized);
    fetchMock.mockResolvedValue(
      createApiResponse(
        {
          code: 201006,
          message: 'session invalid',
          traceId: 'trace-401',
        },
        401,
      ),
    );

    const results = await Promise.allSettled([
      apiRequest('/api/v1/auth/me'),
      apiRequest('/api/v1/orders'),
    ]);

    expect(results.every((result) => result.status === 'rejected')).toBe(true);
    expect(handleUnauthorized).toHaveBeenCalledTimes(1);
    removeHandler();
  });

  it.each([
    [409, 204003, '退款状态冲突', 'trace-409'],
    [422, 201002, '字段校验失败', 'trace-422'],
    [429, 101002, '请求过于频繁', 'trace-429'],
    [503, 301001, '服务暂不可用', 'trace-503'],
  ])('%i 合法失败信封省略 data 时保留错误码和 traceId', async (status, code, message, traceId) => {
    fetchMock.mockResolvedValue(createApiResponse({ code, message, traceId }, status));

    await expect(apiRequest('/api/v1/test')).rejects.toMatchObject({
      kind: 'HTTP',
      code,
      message,
      status,
      traceId,
    });
  });

  it('任意非 2xx 合法错误信封都生成 ApiError', async () => {
    fetchMock.mockResolvedValue(
      createApiResponse(
        { code: 100001, message: '请求参数错误', traceId: 'trace-generic-http' },
        418,
      ),
    );

    await expect(apiRequest('/api/v1/test')).rejects.toMatchObject({
      name: 'ApiError',
      kind: 'HTTP',
      code: 100001,
      status: 418,
      traceId: 'trace-generic-http',
    });
  });

  it.each([
    ['空响应', null],
    ['HTML 响应', '<html><body>proxy failure</body></html>'],
    ['非法 JSON', '{"code":'],
  ])('非 2xx %s 返回固定安全错误', async (_name, body) => {
    fetchMock.mockResolvedValue(createRawResponse(body));

    const request = apiRequest('/api/v1/test');
    await expect(request).rejects.toMatchObject({
      kind: 'INVALID_RESPONSE',
      message: '服务返回了无法识别的数据',
      status: 500,
      traceId: 'trace-from-header',
    });
    await expect(request).rejects.not.toMatchObject({ message: expect.stringContaining('proxy') });
  });

  it('非 2xx JSON 结构完全错误时返回响应格式错误', async () => {
    fetchMock.mockResolvedValue(createApiResponse({ error: 'raw backend detail' }, 500));

    await expect(apiRequest('/api/v1/test')).rejects.toMatchObject({
      kind: 'INVALID_RESPONSE',
      message: '服务返回的数据格式不正确',
      status: 500,
    });
  });

  it('成功响应缺少必需 data 时仍返回响应格式错误', async () => {
    fetchMock.mockResolvedValue(
      createApiResponse({ code: 0, message: 'success', traceId: 'trace-success-missing' }),
    );

    await expect(apiRequest('/api/v1/test')).rejects.toMatchObject({
      kind: 'INVALID_RESPONSE',
      message: '服务返回的数据格式不正确',
      status: 200,
      traceId: 'trace-success-missing',
    });
  });

  it('当前正式接口要求成功数据时，显式 data null 仍返回响应格式错误', async () => {
    fetchMock.mockResolvedValue(
      createApiResponse({ code: 0, message: 'success', data: null, traceId: 'trace-null' }),
    );

    await expect(apiRequest('/api/v1/test')).rejects.toMatchObject({
      kind: 'INVALID_RESPONSE',
      message: '服务返回的数据格式不正确',
      traceId: 'trace-null',
    });
  });

  it('错误消息不包含完整响应正文或敏感字段', async () => {
    const sensitiveBody = '<html>Cookie=secret; JWT=secret; password=secret</html>';
    fetchMock.mockResolvedValue(createRawResponse(sensitiveBody, 503));

    const request = apiRequest('/api/v1/test');
    await expect(request).rejects.toMatchObject({ message: '服务返回了无法识别的数据' });
    await expect(request).rejects.not.toMatchObject({
      message: expect.stringContaining('secret'),
    });
  });
});
