import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getSeatMap, getShows } from './api';
import { useSeatMap, useShows } from './hooks';
import type { SeatMapResponse, ShowSummary } from './types';

vi.mock('./api', () => ({
  getSeatMap: vi.fn(),
  getShows: vi.fn(),
}));

interface Deferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function createDeferred<T>(): Deferred<T> {
  let resolvePromise: ((value: T) => void) | undefined;
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve;
  });
  return {
    promise,
    resolve: (value: T) => resolvePromise?.(value),
  };
}

function createShow(showId: string, movieId: string): ShowSummary {
  return {
    showId,
    movieId,
    cinemaId: 'cinema-1',
    cinemaName: '测试影院',
    auditoriumId: 'auditorium-1',
    auditoriumName: '测试影厅',
    startTime: '2026-08-05T19:00:00+08:00',
    endTime: '2026-08-05T21:00:00+08:00',
    expiresAt: '2026-08-05T18:45:00+08:00',
    languageVersion: '原版 2D',
    basePrice: '39.00',
    availableSeatCount: 10,
    status: 'ON_SALE',
    dataType: 'INITIAL',
    stateVersion: 1,
    updatedAt: '2026-08-05T10:00:00+08:00',
  };
}

function createSeatMap(showId: string): SeatMapResponse {
  return {
    showId,
    auditoriumId: 'auditorium-1',
    auditoriumName: '测试影厅',
    rowCount: 1,
    seatCount: 1,
    availableSeatCount: 1,
    stateVersion: 1,
    updatedAt: '2026-08-05T10:00:00+08:00',
    seats: [
      {
        seatId: `seat-${showId}`,
        rowNo: '1',
        seatNo: '1',
        seatLabel: '1排1座',
        status: 'AVAILABLE',
        stateVersion: 0,
      },
    ],
  };
}

describe('ticketing query hooks', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('后发场次请求先返回时丢弃旧筛选条件的迟到响应', async () => {
    const firstRequest = createDeferred<ShowSummary[]>();
    const secondRequest = createDeferred<ShowSummary[]>();
    vi.mocked(getShows)
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise);

    const { result, rerender } = renderHook(({ movieId }) => useShows(movieId, 'cinema-1'), {
      initialProps: { movieId: 'movie-old' },
    });
    await waitFor(() => expect(getShows).toHaveBeenCalledTimes(1));

    rerender({ movieId: 'movie-new' });
    await waitFor(() => expect(getShows).toHaveBeenCalledTimes(2));

    await act(async () => {
      secondRequest.resolve([createShow('show-new', 'movie-new')]);
      await secondRequest.promise;
    });
    await waitFor(() => expect(result.current.shows[0]?.showId).toBe('show-new'));

    await act(async () => {
      firstRequest.resolve([createShow('show-old', 'movie-old')]);
      await firstRequest.promise;
    });
    expect(result.current.shows[0]?.showId).toBe('show-new');
    expect(result.current.loading).toBe(false);
  });

  it('后发座位请求先返回时丢弃旧场次的迟到响应', async () => {
    const firstRequest = createDeferred<SeatMapResponse>();
    const secondRequest = createDeferred<SeatMapResponse>();
    vi.mocked(getSeatMap)
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise);

    const { result, rerender } = renderHook(({ showId }) => useSeatMap(showId), {
      initialProps: { showId: 'show-old' },
    });
    await waitFor(() => expect(getSeatMap).toHaveBeenCalledTimes(1));

    rerender({ showId: 'show-new' });
    await waitFor(() => expect(getSeatMap).toHaveBeenCalledTimes(2));

    await act(async () => {
      secondRequest.resolve(createSeatMap('show-new'));
      await secondRequest.promise;
    });
    await waitFor(() => expect(result.current.seatMap?.showId).toBe('show-new'));

    await act(async () => {
      firstRequest.resolve(createSeatMap('show-old'));
      await firstRequest.promise;
    });
    expect(result.current.seatMap?.showId).toBe('show-new');
    expect(result.current.loading).toBe(false);
  });
});
