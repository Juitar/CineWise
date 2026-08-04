import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  ApiResult,
  CinemaDetail,
  CinemaSummary,
  ContentFreshness,
  ContentPageResponse,
  MovieDetail,
  MovieSummary,
} from '../types/api';
import { apiRequest } from './client';

const OFFSET_DATE_TIME_PATTERN = /(Z|[+-]\d{2}:\d{2})$/;

function createApiResponse<T>(result: ApiResult<T>): Response {
  return new Response(JSON.stringify(result), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

function expectDemoFreshness(freshness: ContentFreshness): void {
  expect(freshness).toMatchObject({
    source: 'DEMO_CONTENT',
    sourceType: 'MOCK',
    isExpired: false,
    degraded: true,
    fallbackType: 'MOCK',
  });
  expect(freshness.dataTime).toMatch(OFFSET_DATE_TIME_PATTERN);
  expect(freshness.expiresAt).toMatch(OFFSET_DATE_TIME_PATTERN);
  expect(Number.isNaN(Date.parse(freshness.dataTime))).toBe(false);
  expect(Number.isNaN(Date.parse(freshness.expiresAt))).toBe(false);
}

describe('公开内容接口响应契约', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('解析影片分页包装、可空海报和 Demo 标识', async () => {
    const response = {
      code: 0,
      message: 'success',
      data: {
        records: [
          {
            movieId: '8100001',
            title: '星河远征',
            posterUrl: null,
            genres: ['科幻', '冒险'],
            durationMinutes: 128,
            rating: 8.6,
          },
        ],
        total: 1,
        page: 1,
        size: 20,
        source: 'DEMO_CONTENT',
        sourceType: 'MOCK',
        dataTime: '2026-08-04T09:02:03+08:00',
        expiresAt: '2026-08-04T15:02:03+08:00',
        isExpired: false,
        degraded: true,
        fallbackType: 'MOCK',
      },
      traceId: '11111111111111111111111111111111',
    } satisfies ApiResult<ContentPageResponse<MovieSummary>>;
    fetchMock.mockResolvedValue(createApiResponse(response));

    const result = await apiRequest<ContentPageResponse<MovieSummary>>('/api/v1/movies', {
      query: { keyword: '星河', genre: '科幻', page: 1, size: 20 },
    });

    expect(result.records[0]).toMatchObject({ movieId: '8100001', posterUrl: null });
    expect(result).toMatchObject({ total: 1, page: 1, size: 20 });
    expectDemoFreshness(result);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/movies?keyword=%E6%98%9F%E6%B2%B3&genre=%E7%A7%91%E5%B9%BB&page=1&size=20',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('解析影片详情的可空简介及来源时效字段', async () => {
    const response = {
      code: 0,
      message: 'success',
      data: {
        movieId: '8100001',
        title: '星河远征',
        posterUrl: null,
        summary: null,
        genres: ['科幻', '冒险'],
        durationMinutes: 128,
        rating: 8.6,
        source: 'DEMO_CONTENT',
        sourceType: 'MOCK',
        dataTime: '2026-08-04T09:02:03+08:00',
        expiresAt: '2026-08-04T15:02:03+08:00',
        isExpired: false,
        degraded: true,
        fallbackType: 'MOCK',
      },
      traceId: '22222222222222222222222222222222',
    } satisfies ApiResult<MovieDetail>;
    fetchMock.mockResolvedValue(createApiResponse(response));

    const result = await apiRequest<MovieDetail>('/api/v1/movies/8100001');

    expect(result).toMatchObject({ movieId: '8100001', posterUrl: null, summary: null });
    expectDemoFreshness(result);
  });

  it('解析影院分页包装且不引入动态距离字段', async () => {
    const response = {
      code: 0,
      message: 'success',
      data: {
        records: [
          {
            cinemaId: '8200001',
            name: '妙语影城·滨江店',
            cityCode: '330100',
            area: '滨江区',
            address: '江南大道88号',
          },
        ],
        total: 1,
        page: 1,
        size: 20,
        source: 'DEMO_CONTENT',
        sourceType: 'MOCK',
        dataTime: '2026-08-04T09:02:03+08:00',
        expiresAt: '2026-08-04T15:02:03+08:00',
        isExpired: false,
        degraded: true,
        fallbackType: 'MOCK',
      },
      traceId: '33333333333333333333333333333333',
    } satisfies ApiResult<ContentPageResponse<CinemaSummary>>;
    fetchMock.mockResolvedValue(createApiResponse(response));

    const result = await apiRequest<ContentPageResponse<CinemaSummary>>('/api/v1/cinemas', {
      query: { location: '330100', keyword: '滨江', page: 1, size: 20 },
    });

    expect(result.records[0]).not.toHaveProperty('distance');
    expect(result.records[0]).not.toHaveProperty('distanceMeters');
    expect(result).toMatchObject({ total: 1, page: 1, size: 20 });
    expectDemoFreshness(result);
  });

  it('解析影院详情的可空地址字段及 Demo 标识', async () => {
    const response = {
      code: 0,
      message: 'success',
      data: {
        cinemaId: '8200001',
        name: '妙语影城·滨江店',
        cityCode: '330100',
        area: '滨江区',
        address: '江南大道88号',
        source: 'DEMO_CONTENT',
        sourceType: 'MOCK',
        dataTime: '2026-08-04T09:02:03+08:00',
        expiresAt: '2026-08-04T15:02:03+08:00',
        isExpired: false,
        degraded: true,
        fallbackType: 'MOCK',
      },
      traceId: '44444444444444444444444444444444',
    } satisfies ApiResult<CinemaDetail>;
    fetchMock.mockResolvedValue(createApiResponse(response));

    const result = await apiRequest<CinemaDetail>('/api/v1/cinemas/8200001');

    expect(result).toMatchObject({ cinemaId: '8200001', cityCode: '330100' });
    expectDemoFreshness(result);
  });
});
