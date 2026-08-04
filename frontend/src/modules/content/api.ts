import { apiRequest } from '../../shared/api/client';
import type { ContentPageResponse, MovieListQuery, MovieSummary } from '../../shared/types/api';

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
