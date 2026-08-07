import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { MovieDetail } from '../../shared/types/api';
import { useMovieDetail } from './useMovieDetail';

const mocks = vi.hoisted(() => ({ getMovieDetail: vi.fn() }));

vi.mock('./api', () => ({ getMovieDetail: mocks.getMovieDetail }));

function detail(movieId: string, title: string): MovieDetail {
  return {
    movieId,
    title,
    posterUrl: null,
    genres: [],
    durationMinutes: null,
    rating: null,
    summary: null,
    source: 'LIVE',
    sourceType: 'LIVE',
    dataTime: '2026-08-06T09:00:00+08:00',
    expiresAt: '2026-08-07T09:00:00+08:00',
    isExpired: false,
    degraded: false,
    fallbackType: null,
  };
}

describe('useMovieDetail', () => {
  beforeEach(() => mocks.getMovieDetail.mockReset());

  it('影片切换时取消旧请求且只保留当前响应', async () => {
    let resolveFirst: ((value: MovieDetail) => void) | undefined;
    mocks.getMovieDetail
      .mockImplementationOnce(
        () =>
          new Promise<MovieDetail>((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockResolvedValueOnce(detail('2', '第二部影片'));
    const { result, rerender } = renderHook(({ movieId }) => useMovieDetail(movieId), {
      initialProps: { movieId: '1' },
    });
    const firstSignal = mocks.getMovieDetail.mock.calls[0][1] as AbortSignal;

    rerender({ movieId: '2' });
    expect(firstSignal.aborted).toBe(true);
    await waitFor(() => expect(result.current.data?.movieId).toBe('2'));
    await act(async () => {
      resolveFirst?.(detail('1', '第一部影片'));
      await Promise.resolve();
    });
    expect(result.current.data?.title).toBe('第二部影片');
  });
});
