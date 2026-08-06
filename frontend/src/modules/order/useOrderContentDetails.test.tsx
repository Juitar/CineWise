import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CinemaDetail, MovieDetail } from '../../shared/types/api';
import { useOrderContentDetails } from './useOrderContentDetails';

const mocks = vi.hoisted(() => ({
  getMovieDetail: vi.fn(),
  getCinemaDetail: vi.fn(),
}));

vi.mock('../content/api', () => mocks);

const movie: MovieDetail = {
  movieId: '1',
  title: '测试影片',
  posterUrl: null,
  genres: [],
  durationMinutes: null,
  rating: null,
  summary: null,
  source: 'DEMO_CONTENT',
  sourceType: 'MOCK',
  dataTime: '2026-08-06T09:00:00+08:00',
  expiresAt: '2026-08-06T15:00:00+08:00',
  isExpired: false,
  degraded: false,
  fallbackType: null,
};

const cinema: CinemaDetail = {
  cinemaId: '2',
  name: '测试影院',
  cityCode: null,
  area: '测试区域',
  address: '测试地址',
  source: 'DEMO_CONTENT',
  sourceType: 'MOCK',
  dataTime: '2026-08-06T09:00:00+08:00',
  expiresAt: '2026-08-06T15:00:00+08:00',
  isExpired: false,
  degraded: false,
  fallbackType: null,
};

const nextMovie: MovieDetail = { ...movie, movieId: '3', title: '下一部影片' };
const nextCinema: CinemaDetail = { ...cinema, cinemaId: '4', name: '下一家影院' };

describe('useOrderContentDetails', () => {
  beforeEach(() => {
    mocks.getMovieDetail.mockReset();
    mocks.getCinemaDetail.mockReset();
  });

  it('去重查询并返回影片和影院资料', async () => {
    mocks.getMovieDetail.mockResolvedValue(movie);
    mocks.getCinemaDetail.mockResolvedValue(cinema);

    const { result } = renderHook(() =>
      useOrderContentDetails([
        { movieId: '1', cinemaId: '2' },
        { movieId: '1', cinemaId: '2' },
      ]),
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mocks.getMovieDetail).toHaveBeenCalledTimes(1);
    expect(mocks.getCinemaDetail).toHaveBeenCalledTimes(1);
    expect(result.current.moviesById.get('1')).toEqual(movie);
    expect(result.current.cinemasById.get('2')).toEqual(cinema);
    expect(result.current.hasUnavailableContent).toBe(false);
  });

  it('单项失败只降级内容并支持重新查询', async () => {
    mocks.getMovieDetail.mockRejectedValue(new Error('影片暂不可用'));
    mocks.getCinemaDetail.mockResolvedValue(cinema);

    const { result } = renderHook(() => useOrderContentDetails([{ movieId: '1', cinemaId: '2' }]));

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.hasUnavailableContent).toBe(true);
    expect(result.current.cinemasById.get('2')).toEqual(cinema);

    mocks.getMovieDetail.mockResolvedValue(movie);
    result.current.refresh();
    await waitFor(() => expect(result.current.moviesById.get('1')).toEqual(movie));
    expect(mocks.getMovieDetail).toHaveBeenCalledTimes(2);
  });

  it('快速切换订单时忽略旧内容响应', async () => {
    let resolvePreviousMovie: ((detail: MovieDetail) => void) | undefined;
    let resolvePreviousCinema: ((detail: CinemaDetail) => void) | undefined;
    const previousMovie = new Promise<MovieDetail>((resolve) => {
      resolvePreviousMovie = resolve;
    });
    const previousCinema = new Promise<CinemaDetail>((resolve) => {
      resolvePreviousCinema = resolve;
    });
    mocks.getMovieDetail.mockImplementation((movieId: string) =>
      movieId === '1' ? previousMovie : Promise.resolve(nextMovie),
    );
    mocks.getCinemaDetail.mockImplementation((cinemaId: string) =>
      cinemaId === '2' ? previousCinema : Promise.resolve(nextCinema),
    );

    const { result, rerender } = renderHook(
      ({ references }: { references: Array<{ movieId: string; cinemaId: string }> }) =>
        useOrderContentDetails(references),
      { initialProps: { references: [{ movieId: '1', cinemaId: '2' }] } },
    );
    rerender({ references: [{ movieId: '3', cinemaId: '4' }] });

    await waitFor(() => expect(result.current.moviesById.get('3')).toEqual(nextMovie));
    await act(async () => {
      resolvePreviousMovie?.(movie);
      resolvePreviousCinema?.(cinema);
    });

    expect(result.current.moviesById.get('3')).toEqual(nextMovie);
    expect(result.current.moviesById.has('1')).toBe(false);
    expect(result.current.cinemasById.get('4')).toEqual(nextCinema);
  });
});
