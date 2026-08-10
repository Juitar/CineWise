/** 后端统一 REST 响应。成功时 code 为 0，业务失败由公共请求层转成 ApiError。 */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

/** 后端统一分页结果，page 从 1 开始。 */
export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}

/** 内容来源类型；MOCK 数据必须在页面上明确标识为演示数据。 */
export type ContentSourceType = 'LIVE' | 'MOCK' | 'SNAPSHOT';

/** 内容查询未直接取得实时数据时实际使用的回退来源。 */
export type ContentFallbackType = 'CACHE' | 'MOCK' | 'SNAPSHOT';

/**
 * 内容接口统一的来源与时效信息。
 *
 * dataTime、expiresAt 使用带时区偏移的 ISO 8601 字符串；API 层固定返回 isExpired，
 * 不能把后端内部字段 expired 直接暴露给前端。
 */
export interface ContentFreshness {
  source: string;
  sourceType: ContentSourceType;
  dataTime: string;
  expiresAt: string;
  isExpired: boolean;
  degraded: boolean;
  fallbackType: ContentFallbackType | null;
}

/** 影片、影院列表统一分页包装，来源与时效信息属于本次查询结果。 */
export interface ContentPageResponse<T> extends PageResult<T>, ContentFreshness {}

/** 影片列表记录；当前无海报来源时 posterUrl 为 null，页面使用默认海报。 */
export interface MovieSummary {
  movieId: string;
  title: string;
  posterUrl: string | null;
  genres: string[];
  durationMinutes: number | null;
  rating: number | null;
}

/** 影片详情在列表字段基础上增加简介及来源时效信息。 */
export interface MovieDetail extends MovieSummary, ContentFreshness {
  summary: string | null;
}

/** 影院列表记录；距离属于路线能力，不进入内容基础 DTO。 */
export interface CinemaSummary {
  cinemaId: string;
  name: string;
  cityCode: string | null;
  area: string | null;
  address: string | null;
}

/** 影院详情复用基础展示字段，并携带用于地图标记的静态坐标和来源时效信息。 */
export interface CinemaDetail extends CinemaSummary, ContentFreshness {
  longitude: number | null;
  latitude: number | null;
}

/** GET /api/v1/movies 的可选查询参数。 */
export interface MovieListQuery {
  keyword?: string;
  genre?: string;
  page?: number;
  size?: number;
}

/** GET /api/v1/cinemas 的查询参数；location 固定表示城市行政区划代码。 */
export interface CinemaListQuery {
  location: string;
  keyword?: string;
  page?: number;
  size?: number;
}
