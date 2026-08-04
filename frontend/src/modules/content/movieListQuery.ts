import type { MovieListQuery } from '../../shared/types/api';

export const DEFAULT_MOVIE_PAGE = 1;
export const DEFAULT_MOVIE_PAGE_SIZE = 20;
export const MAX_MOVIE_PAGE_SIZE = 50;
export const MAX_MOVIE_FILTER_LENGTH = 100;

/** 已经过长度和数值范围校验、可以直接交给影片 API 的查询条件。 */
export interface NormalizedMovieListQuery extends MovieListQuery {
  page: number;
  size: number;
}

/** URL 解析结果；issues 只用于提示被替换的非法参数，不会把原值继续发送给后端。 */
export interface MovieListQueryParseResult {
  issues: string[];
  query: NormalizedMovieListQuery;
}

/**
 * 解析只允许十进制整数的分页参数。
 *
 * 先检查字符串外形再转 number，避免小数、科学计数法和超出安全整数范围的值进入后端查询。
 */
function parseIntegerInRange(
  rawValue: string | null,
  defaultValue: number,
  minimum: number,
  maximum: number,
  fieldName: string,
  issues: string[],
): number {
  if (rawValue === null || rawValue === '') {
    return defaultValue;
  }

  if (!/^\d+$/.test(rawValue)) {
    issues.push(`${fieldName} 格式不正确`);
    return defaultValue;
  }

  const value = Number(rawValue);
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) {
    issues.push(`${fieldName} 超出允许范围`);
    return defaultValue;
  }

  return value;
}

/** 规范化可选筛选文本；空白等同未传，超长值回退为空并记录提示。 */
function parseFilter(
  rawValue: string | null,
  fieldName: string,
  issues: string[],
): string | undefined {
  if (rawValue === null) {
    return undefined;
  }

  const value = rawValue.trim();
  if (value.length === 0) {
    return undefined;
  }
  if (value.length > MAX_MOVIE_FILTER_LENGTH) {
    issues.push(`${fieldName} 最长 ${MAX_MOVIE_FILTER_LENGTH} 个字符`);
    return undefined;
  }
  return value;
}

/**
 * 将 URL 查询参数转换为后端可以直接接收的影片查询条件。
 *
 * URL 属于用户可修改输入，不能直接信任；非法值在发请求前替换为安全默认值。
 */
export function parseMovieListQuery(searchParams: URLSearchParams): MovieListQueryParseResult {
  const issues: string[] = [];
  const keyword = parseFilter(searchParams.get('keyword'), '关键词', issues);
  const genre = parseFilter(searchParams.get('genre'), '影片类型', issues);
  const page = parseIntegerInRange(
    searchParams.get('page'),
    DEFAULT_MOVIE_PAGE,
    1,
    Number.MAX_SAFE_INTEGER,
    '页码',
    issues,
  );
  const size = parseIntegerInRange(
    searchParams.get('size'),
    DEFAULT_MOVIE_PAGE_SIZE,
    1,
    MAX_MOVIE_PAGE_SIZE,
    '每页数量',
    issues,
  );

  return {
    issues,
    query: {
      genre,
      keyword,
      page,
      size,
    },
  };
}

/**
 * 生成可分享的影片筛选 URL。
 *
 * 默认分页值省略后仍由解析器恢复为 1/20，因此刷新语义不变，也不会产生无意义参数。
 */
export function buildMovieListSearchParams(query: NormalizedMovieListQuery): URLSearchParams {
  const searchParams = new URLSearchParams();
  if (query.keyword) {
    searchParams.set('keyword', query.keyword);
  }
  if (query.genre) {
    searchParams.set('genre', query.genre);
  }
  if (query.page !== DEFAULT_MOVIE_PAGE) {
    searchParams.set('page', String(query.page));
  }
  if (query.size !== DEFAULT_MOVIE_PAGE_SIZE) {
    searchParams.set('size', String(query.size));
  }
  return searchParams;
}
