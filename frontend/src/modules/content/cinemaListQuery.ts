import type { CinemaListQuery } from '../../shared/types/api';

export const DEFAULT_CINEMA_LOCATION = '430100';
export const DEFAULT_CINEMA_PAGE = 1;
export const DEFAULT_CINEMA_PAGE_SIZE = 20;
export const MAX_CINEMA_PAGE_SIZE = 50;
export const MAX_CINEMA_FILTER_LENGTH = 100;

/** 已校验、可以直接交给影院 API 的查询条件。 */
export interface NormalizedCinemaListQuery extends CinemaListQuery {
  page: number;
  size: number;
}

/** URL 解析结果；非法值会被安全默认值替换，并保留可展示的问题说明。 */
export interface CinemaListQueryParseResult {
  issues: string[];
  query: NormalizedCinemaListQuery;
}

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

function parseKeyword(rawValue: string | null, issues: string[]): string | undefined {
  if (rawValue === null) {
    return undefined;
  }
  const keyword = rawValue.trim();
  if (keyword.length === 0) {
    return undefined;
  }
  if (keyword.length > MAX_CINEMA_FILTER_LENGTH) {
    issues.push(`关键词最长 ${MAX_CINEMA_FILTER_LENGTH} 个字符`);
    return undefined;
  }
  return keyword;
}

/** 将可修改的 URL 输入转换为后端接受的影院查询条件。 */
export function parseCinemaListQuery(searchParams: URLSearchParams): CinemaListQueryParseResult {
  const issues: string[] = [];
  const rawLocation = searchParams.get('location');
  let location = rawLocation?.trim() || DEFAULT_CINEMA_LOCATION;
  if (!/^\d{6}$/.test(location)) {
    issues.push('城市代码必须是六位数字');
    location = DEFAULT_CINEMA_LOCATION;
  }

  return {
    issues,
    query: {
      keyword: parseKeyword(searchParams.get('keyword'), issues),
      location,
      page: parseIntegerInRange(
        searchParams.get('page'),
        DEFAULT_CINEMA_PAGE,
        1,
        Number.MAX_SAFE_INTEGER,
        '页码',
        issues,
      ),
      size: parseIntegerInRange(
        searchParams.get('size'),
        DEFAULT_CINEMA_PAGE_SIZE,
        1,
        MAX_CINEMA_PAGE_SIZE,
        '每页数量',
        issues,
      ),
    },
  };
}

/** 生成可刷新、可分享的影院查询 URL；location 始终保留，避免城市含义丢失。 */
export function buildCinemaListSearchParams(query: NormalizedCinemaListQuery): URLSearchParams {
  const searchParams = new URLSearchParams({ location: query.location });
  if (query.keyword) {
    searchParams.set('keyword', query.keyword);
  }
  if (query.page !== DEFAULT_CINEMA_PAGE) {
    searchParams.set('page', String(query.page));
  }
  if (query.size !== DEFAULT_CINEMA_PAGE_SIZE) {
    searchParams.set('size', String(query.size));
  }
  return searchParams;
}
