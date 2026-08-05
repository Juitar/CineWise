import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ContentPageResponse, MovieSummary } from '../../shared/types/api';
import { queryMovies } from './api';

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
