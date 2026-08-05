import { apiRequest } from '../../shared/api/client';
import type {
  CinemaListQuery,
  CinemaDetail,
  CinemaSummary,
  ContentPageResponse,
  MovieListQuery,
  MovieSummary,
} from '../../shared/types/api';

/** 查询公开影院详情；来源和更新时间由详情响应自身决定。 */
export function queryCinemaDetail(cinemaId: string, signal?: AbortSignal): Promise<CinemaDetail> {
  return apiRequest<CinemaDetail>(`/api/v1/cinemas/${encodeURIComponent(cinemaId)}`, { signal });
}

/**
 * 查询公开影院列表。
 *
 * location 使用六位行政代码；长沙为 430100，第三方城市 ID 不得传到前端。
 */
export function queryCinemas(
  query: Required<Pick<CinemaListQuery, 'location' | 'page' | 'size'>> & CinemaListQuery,
  signal?: AbortSignal,
): Promise<ContentPageResponse<CinemaSummary>> {
  return apiRequest<ContentPageResponse<CinemaSummary>>('/api/v1/cinemas', {
    query: {
      keyword: query.keyword,
      location: query.location,
      page: query.page,
      size: query.size,
    },
    signal,
  });
}

/**
 * 查询公开影片列表。
 *
 * 查询只使用后端已经支持的 keyword、genre、page 和 size，不在前端对分页结果二次筛选。
 * signal 由查询 Hook 管理；筛选变化或页面卸载时取消旧请求，取消后不会自动重发。
 */
export function queryMovies(
  query: Required<Pick<MovieListQuery, 'page' | 'size'>> & MovieListQuery,
  signal?: AbortSignal,
): Promise<ContentPageResponse<MovieSummary>> {
  return apiRequest<ContentPageResponse<MovieSummary>>('/api/v1/movies', {
    query: {
      genre: query.genre,
      keyword: query.keyword,
      page: query.page,
      size: query.size,
    },
    signal,
  });
}
