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
        data: null,
        traceId: 'trace-2',
      }),
    );

    await expect(apiRequest('/api/v1/shows/3001')).rejects.toMatchObject({
      kind: 'BUSINESS',
      code: 200101,
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
          data: null,
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
});
