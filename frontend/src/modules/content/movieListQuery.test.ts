import { describe, expect, it } from 'vitest';

import {
  buildMovieListSearchParams,
  parseMovieListQuery,
  type NormalizedMovieListQuery,
} from './movieListQuery';

describe('影片列表 URL 查询条件', () => {
  it('缺少参数时使用后端默认分页', () => {
    expect(parseMovieListQuery(new URLSearchParams())).toEqual({
      issues: [],
      query: {
        genre: undefined,
        keyword: undefined,
        page: 1,
        size: 20,
      },
    });
  });

  it('规范化筛选并保留合法分页', () => {
    const result = parseMovieListQuery(
      new URLSearchParams(
        'keyword=%20%E6%98%9F%E6%B2%B3%20&genre=%E7%A7%91%E5%B9%BB&page=3&size=10',
      ),
    );

    expect(result).toEqual({
      issues: [],
      query: {
        keyword: '星河',
        genre: '科幻',
        page: 3,
        size: 10,
      },
    });
  });

  it('非法参数使用安全默认值且不继续提交原值', () => {
    const result = parseMovieListQuery(
      new URLSearchParams(`keyword=${'a'.repeat(101)}&page=0&size=99`),
    );

    expect(result.query).toEqual({
      keyword: undefined,
      genre: undefined,
      page: 1,
      size: 20,
    });
    expect(result.issues).toHaveLength(3);
  });

  it('生成可分享 URL 并省略默认值', () => {
    const query: NormalizedMovieListQuery = {
      keyword: '星河',
      genre: '科幻',
      page: 2,
      size: 20,
    };

    expect(buildMovieListSearchParams(query).toString()).toBe(
      'keyword=%E6%98%9F%E6%B2%B3&genre=%E7%A7%91%E5%B9%BB&page=2',
    );
  });
});
