import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { apiRequest } from './client';

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
    fetchMock.mockImplementation(
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

    await vi.advanceTimersByTimeAsync(50);
    await expectation;
  });
});
