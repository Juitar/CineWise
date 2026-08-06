import { apiRequest } from '../../shared/api/client';
import type {
  CinemaListQuery,
  CinemaDetail,
  CinemaSummary,
  ContentPageResponse,
  MovieDetail,
  MovieListQuery,
  MovieSummary,
} from '../../shared/types/api';

/**
 * 读取影片详情的基础展示资料。
 *
 * 该函数只请求内容模块已声明的标题、海报和来源时效；不补充或推断场次、价格、座位、库存和距离。
 * 调用方负责加载、404、网络失败和重试状态，并可用 signal 取消失效的只读请求。
 */
export function getMovieDetail(movieId: string, signal?: AbortSignal): Promise<MovieDetail> {
  return apiRequest<MovieDetail>(`/api/v1/movies/${encodeURIComponent(movieId)}`, { signal });
}

/**
 * 读取影院详情的基础展示资料。
 *
 * 结果仅包含影院名称、区域、地址和来源时效；票务场次、价格、座位、库存和距离仍由所属模块单独查询。
 * 调用方负责加载、404、网络失败和重试状态，并可用 signal 取消失效的只读请求。
 */
export function getCinemaDetail(cinemaId: string, signal?: AbortSignal): Promise<CinemaDetail> {
  return apiRequest<CinemaDetail>(`/api/v1/cinemas/${encodeURIComponent(cinemaId)}`, { signal });
}

/**
 * 保留旧名称，避免现有调用方在 A 切换到 getCinemaDetail 前出现构建中断。
 *
 * 新增调用应使用 getCinemaDetail；此别名不新增请求逻辑，也不会改变公共鉴权或路由。
 */
export function queryCinemaDetail(cinemaId: string, signal?: AbortSignal): Promise<CinemaDetail> {
  return getCinemaDetail(cinemaId, signal);
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
