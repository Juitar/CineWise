import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import { useAvailableMovies } from './useAvailableMovies';

const mocks = vi.hoisted(() => ({ getAvailableMovies: vi.fn() }));

vi.mock('./api', () => ({ getAvailableMovies: mocks.getAvailableMovies }));

const response = {
  movies: [
    {
      movieId: '10',
      title: '演示影片',
      posterUrl: null,
      showCount: 6,
      nearestStartTime: '2026-08-05T19:30:00+08:00',
      contentSource: 'NETSTART_MAOYAN',
      contentDataTime: '2026-08-05T09:00:00+08:00',
      scheduleSource: 'demo-seed',
      scheduleDataTime: '2026-08-05T10:00:00+08:00',
    },
  ],
};

describe('useAvailableMovies', () => {
  beforeEach(() => mocks.getAvailableMovies.mockReset());

  it('排期失败后可以按原影院主动重试', async () => {
    mocks.getAvailableMovies
      .mockRejectedValueOnce(new ApiError('offline', { kind: 'NETWORK' }))
      .mockResolvedValueOnce(response);
    const { result } = renderHook(() => useAvailableMovies('20'));

    await waitFor(() => expect(result.current.error?.kind).toBe('NETWORK'));
    act(() => result.current.retry());
    await waitFor(() => expect(result.current.movies).toEqual(response.movies));
    expect(mocks.getAvailableMovies).toHaveBeenNthCalledWith(2, '20', expect.any(AbortSignal));
  });
});
