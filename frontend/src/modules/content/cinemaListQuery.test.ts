import { describe, expect, it } from 'vitest';

import {
  buildCinemaListSearchParams,
  DEFAULT_CINEMA_LOCATION,
  parseCinemaListQuery,
} from './cinemaListQuery';

describe('parseCinemaListQuery', () => {
  it('缺少参数时使用长沙和默认分页', () => {
    expect(parseCinemaListQuery(new URLSearchParams())).toEqual({
      issues: [],
      query: {
        keyword: undefined,
        location: DEFAULT_CINEMA_LOCATION,
        page: 1,
        size: 20,
      },
    });
  });

  it('修正非法城市和分页且不继续发送原值', () => {
    const result = parseCinemaListQuery(
      new URLSearchParams('location=hangzhou&page=0&size=99&keyword=%20%E6%BB%A8%E6%B1%9F%20'),
    );

    expect(result.query).toEqual({
      keyword: '滨江',
      location: DEFAULT_CINEMA_LOCATION,
      page: 1,
      size: 20,
    });
    expect(result.issues).toHaveLength(3);
  });

  it('将未知六位城市代码降级为长沙，保证显示与请求城市一致', () => {
    const result = parseCinemaListQuery(new URLSearchParams('location=310000'));

    expect(result.query.location).toBe(DEFAULT_CINEMA_LOCATION);
    expect(result.issues).toContain('城市代码仅支持长沙或杭州');
  });

  it('拒绝超长关键词', () => {
    const result = parseCinemaListQuery(
      new URLSearchParams({ keyword: '影'.repeat(101), location: '430100' }),
    );

    expect(result.query.keyword).toBeUndefined();
    expect(result.issues).toContain('关键词最长 100 个字符');
  });
});

describe('buildCinemaListSearchParams', () => {
  it('始终保留城市并省略默认分页', () => {
    const result = buildCinemaListSearchParams({
      keyword: '滨江',
      location: '430100',
      page: 1,
      size: 20,
    });

    expect(result.toString()).toBe('location=430100&keyword=%E6%BB%A8%E6%B1%9F');
  });
});
