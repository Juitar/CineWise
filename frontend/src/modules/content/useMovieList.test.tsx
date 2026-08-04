import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { ContentPageResponse, MovieSummary } from '../../shared/types/api';
import { useMovieList } from './useMovieList';

const contentMocks = vi.hoisted(() => ({
  queryMovies: vi.fn(),
}));

vi.mock('./api', () => ({
  queryMovies: contentMocks.queryMovies,
}));

const response: ContentPageResponse<MovieSummary> = {
  records: [
    {
      movieId: '8100001',
      title: '星河远征',
      posterUrl: null,
      genres: ['科幻'],
      durationMinutes: 128,
      rating: 8.6,
    },
  ],
  total: 1,
  page: 1,
  size: 20,
  source: 'DEMO_CONTENT',
  sourceType: 'MOCK',
  dataTime: '2026-08-04T09:00:00+08:00',
  expiresAt: '2026-08-04T15:00:00+08:00',
  isExpired: false,
  degraded: true,
  fallbackType: 'MOCK',
};

describe('useMovieList', () => {
  beforeEach(() => {
    contentMocks.queryMovies.mockReset();
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true });
  });

  it('加载实际响应并转为成功状态', async () => {
    contentMocks.queryMovies.mockResolvedValue(response);
    const { result } = renderHook(() =>
      useMovieList({ keyword: undefined, genre: undefined, page: 1, size: 20 }),
    );

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.data).toBe(response));
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('条件改变时取消旧请求且旧响应不能覆盖新结果', async () => {
    let resolveFirst: ((value: ContentPageResponse<MovieSummary>) => void) | undefined;
    const secondResponse = {
      ...response,
      records: [{ ...response.records[0], title: '第二部影片' }],
    };
    contentMocks.queryMovies
      .mockImplementationOnce(
        () =>
          new Promise<ContentPageResponse<MovieSummary>>((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockResolvedValueOnce(secondResponse);
    const { result, rerender } = renderHook(
      ({ genre }) => useMovieList({ genre, keyword: undefined, page: 1, size: 20 }),
      { initialProps: { genre: '科幻' as string | undefined } },
    );
    const firstSignal = contentMocks.queryMovies.mock.calls[0][1] as AbortSignal;

    rerender({ genre: '喜剧' });
    expect(firstSignal.aborted).toBe(true);
    await waitFor(() => expect(result.current.data).toBe(secondResponse));

    await act(async () => {
      resolveFirst?.(response);
      await Promise.resolve();
    });
    expect(result.current.data).toBe(secondResponse);
  });

  it('失败后保留旧数据并允许手动重试', async () => {
    contentMocks.queryMovies
      .mockResolvedValueOnce(response)
      .mockRejectedValueOnce(new ApiError('offline', { kind: 'NETWORK' }))
      .mockResolvedValueOnce(response);
    const { result } = renderHook(() =>
      useMovieList({ keyword: undefined, genre: undefined, page: 1, size: 20 }),
    );
    await waitFor(() => expect(result.current.data).toBe(response));

    act(() => result.current.retry());
    await waitFor(() => expect(result.current.error?.kind).toBe('NETWORK'));
    expect(result.current.data).toBe(response);

    act(() => result.current.retry());
    await waitFor(() => expect(result.current.error).toBeNull());
    expect(contentMocks.queryMovies).toHaveBeenCalledTimes(3);
  });

  it('已有数据后离线时标记为内存快照', async () => {
    contentMocks.queryMovies.mockResolvedValue(response);
    const { result } = renderHook(() =>
      useMovieList({ keyword: undefined, genre: undefined, page: 1, size: 20 }),
    );
    await waitFor(() => expect(result.current.data).toBe(response));

    act(() => {
      Object.defineProperty(navigator, 'onLine', { configurable: true, value: false });
      window.dispatchEvent(new Event('offline'));
    });

    expect(result.current.isOffline).toBe(true);
    expect(result.current.isOfflineSnapshot).toBe(true);
  });
});
