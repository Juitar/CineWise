import { beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  CinemaDetail,
  CinemaSummary,
  ContentPageResponse,
  MovieDetail,
  MovieSummary,
} from '../../shared/types/api';
import { getCinemaDetail, getMovieDetail, queryCinemas, queryMovies } from './api';

const apiMocks = vi.hoisted(() => ({
  apiRequest: vi.fn(),
}));

vi.mock('../../shared/api/client', () => ({
  apiRequest: apiMocks.apiRequest,
}));

describe('queryMovies', () => {
  beforeEach(() => {
    apiMocks.apiRequest.mockReset();
  });

  it('通过公共请求层提交后端支持的影片筛选', async () => {
    const response: ContentPageResponse<MovieSummary> = {
      records: [],
      total: 0,
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
    apiMocks.apiRequest.mockResolvedValue(response);
    const controller = new AbortController();

    await expect(
      queryMovies({ keyword: '星河', genre: '科幻', page: 1, size: 20 }, controller.signal),
    ).resolves.toBe(response);

    expect(apiMocks.apiRequest).toHaveBeenCalledWith('/api/v1/movies', {
      query: {
        genre: '科幻',
        keyword: '星河',
        page: 1,
        size: 20,
      },
      signal: controller.signal,
    });
  });
});

describe('内容详情查询', () => {
  beforeEach(() => {
    apiMocks.apiRequest.mockReset();
  });

  it('通过公共请求层读取影片基础资料并传递取消信号', async () => {
    const response: MovieDetail = {
      movieId: '8100001',
      title: '星河远征',
      posterUrl: 'https://example.test/poster.jpg',
      genres: ['科幻'],
      durationMinutes: 120,
      rating: 8.6,
      summary: '用于测试的影片简介',
      source: 'NETSTART_MAOYAN',
      sourceType: 'LIVE',
      dataTime: '2026-08-06T09:00:00+08:00',
      expiresAt: '2026-08-06T15:00:00+08:00',
      isExpired: false,
      degraded: false,
      fallbackType: null,
    };
    apiMocks.apiRequest.mockResolvedValue(response);
    const controller = new AbortController();

    await expect(getMovieDetail('8100001', controller.signal)).resolves.toBe(response);

    expect(apiMocks.apiRequest).toHaveBeenCalledWith('/api/v1/movies/8100001', {
      signal: controller.signal,
    });
  });

  it('通过公共请求层读取影院基础资料并传递取消信号', async () => {
    const response: CinemaDetail = {
      cinemaId: '8200001',
      name: '长沙影城',
      cityCode: '430100',
      area: '岳麓区',
      address: '测试路 1 号',
      longitude: null,
      latitude: null,
      source: 'NETSTART_MAOYAN',
      sourceType: 'LIVE',
      dataTime: '2026-08-06T09:00:00+08:00',
      expiresAt: '2026-08-06T15:00:00+08:00',
      isExpired: false,
      degraded: false,
      fallbackType: null,
    };
    apiMocks.apiRequest.mockResolvedValue(response);
    const controller = new AbortController();

    await expect(getCinemaDetail('8200001', controller.signal)).resolves.toBe(response);

    expect(apiMocks.apiRequest).toHaveBeenCalledWith('/api/v1/cinemas/8200001', {
      signal: controller.signal,
    });
  });
});

describe('queryCinemas', () => {
  beforeEach(() => {
    apiMocks.apiRequest.mockReset();
  });

  it('通过公共请求层提交长沙影院筛选', async () => {
    const response: ContentPageResponse<CinemaSummary> = {
      records: [],
      total: 0,
      page: 1,
      size: 20,
      source: 'NETSTART_MAOYAN',
      sourceType: 'LIVE',
      dataTime: '2026-08-05T09:00:00+08:00',
      expiresAt: '2026-08-05T15:00:00+08:00',
      isExpired: false,
      degraded: false,
      fallbackType: null,
    };
    apiMocks.apiRequest.mockResolvedValue(response);
    const controller = new AbortController();

    await expect(
      queryCinemas({ keyword: '长沙', location: '430100', page: 1, size: 20 }, controller.signal),
    ).resolves.toBe(response);

    expect(apiMocks.apiRequest).toHaveBeenCalledWith('/api/v1/cinemas', {
      query: {
        keyword: '长沙',
        location: '430100',
        page: 1,
        size: 20,
      },
      signal: controller.signal,
    });
  });
});
