import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { ContentPageResponse, MovieSummary } from '../../shared/types/api';
import type { MovieListState } from '../../modules/content/useMovieList';

const pageMocks = vi.hoisted(() => ({
  search: '',
  setSearchParams: vi.fn(),
  useMovieList: vi.fn(),
}));

vi.mock('umi', () => ({
  Link: ({
    children,
    to,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to: string }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useSearchParams: () => [new URLSearchParams(pageMocks.search), pageMocks.setSearchParams],
}));

vi.mock('../../modules/content/useMovieList', () => ({
  useMovieList: pageMocks.useMovieList,
}));

import MoviesPage from './index';

const response: ContentPageResponse<MovieSummary> = {
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
  dataTime: '2026-08-04T09:00:00+08:00',
  expiresAt: '2026-08-04T15:00:00+08:00',
  isExpired: false,
  degraded: true,
  fallbackType: 'MOCK',
};

function movieListState(overrides: Partial<MovieListState> = {}): MovieListState {
  return {
    data: response,
    error: null,
    isLoading: false,
    isOffline: false,
    isOfflineSnapshot: false,
    isRefreshing: false,
    retry: vi.fn(),
    ...overrides,
  };
}

describe('MoviesPage', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));
    pageMocks.search = '';
    pageMocks.setSearchParams.mockReset();
    pageMocks.useMovieList.mockReset();
    pageMocks.useMovieList.mockReturnValue(movieListState());
  });

  it('使用后端影片响应渲染卡片和来源提示', () => {
    render(<MoviesPage />);

    expect(screen.getByRole('heading', { level: 1, name: '影片列表' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '星河远征' })).toBeInTheDocument();
    expect(screen.getByText('演示数据')).toBeInTheDocument();
    expect(screen.getByText('共 1 部影片')).toBeInTheDocument();
    expect(screen.queryByText('云边有个小卖部')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '为《星河远征》选择影院' })).toHaveAttribute(
      'href',
      '/movies/8100001',
    );
  });

  it('选择影片类型时写入 genre 并重置页码', () => {
    pageMocks.search = 'keyword=%E6%98%9F%E6%B2%B3&page=3';
    render(<MoviesPage />);

    fireEvent.click(screen.getByRole('button', { name: '科幻' }));

    const nextParams = pageMocks.setSearchParams.mock.calls[0][0] as URLSearchParams;
    expect(nextParams.get('keyword')).toBe('星河');
    expect(nextParams.get('genre')).toBe('科幻');
    expect(nextParams.get('page')).toBeNull();
  });

  it('提交关键词时去除空白并从第一页查询', () => {
    pageMocks.search = 'genre=%E7%A7%91%E5%B9%BB&page=2';
    render(<MoviesPage />);

    fireEvent.change(screen.getByRole('searchbox', { name: '搜索影片名称' }), {
      target: { value: ' 星河 ' },
    });
    fireEvent.click(screen.getByRole('button', { name: /搜\s*索/ }));

    const nextParams = pageMocks.setSearchParams.mock.calls[0][0] as URLSearchParams;
    expect(nextParams.get('keyword')).toBe('星河');
    expect(nextParams.get('genre')).toBe('科幻');
    expect(nextParams.get('page')).toBeNull();
  });

  it('首次加载、空结果和非法 URL 都有明确状态', () => {
    pageMocks.search = 'page=0&size=99';
    pageMocks.useMovieList.mockReturnValue(movieListState({ data: null, isLoading: true }));
    const { rerender } = render(<MoviesPage />);

    expect(screen.getByRole('status', { name: '影片加载中' })).toBeInTheDocument();
    expect(screen.getByText('部分地址参数无效，已使用安全默认值')).toBeInTheDocument();

    pageMocks.useMovieList.mockReturnValue(
      movieListState({ data: { ...response, records: [], total: 0 } }),
    );
    rerender(<MoviesPage />);
    expect(screen.getByText('暂无影片')).toBeInTheDocument();
    expect(
      screen.getByText('没有找到符合条件的影片，可以调整搜索词或影片类型。'),
    ).toBeInTheDocument();
  });

  it('有旧数据刷新时保留影片、分页和来源说明', () => {
    pageMocks.useMovieList.mockReturnValue(movieListState({ isRefreshing: true }));
    const { rerender } = render(<MoviesPage />);

    expect(screen.queryByRole('status', { name: '影片加载中' })).not.toBeInTheDocument();
    expect(screen.getByText('正在刷新')).toBeInTheDocument();
    expect(screen.getByText('正在更新影片列表，当前影片仍可查看。')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '星河远征' })).toBeInTheDocument();
    expect(screen.getByText('共 1 部影片')).toBeInTheDocument();
    expect(screen.getByText('演示数据')).toBeInTheDocument();

    pageMocks.useMovieList.mockReturnValue(
      movieListState({
        data: {
          ...response,
          records: [{ ...response.records[0], movieId: '8100002', title: '山海新篇' }],
        },
      }),
    );
    rerender(<MoviesPage />);

    expect(screen.queryByRole('status', { name: '影片加载中' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '山海新篇' })).toBeInTheDocument();
  });

  it('当前页为空但仍有总记录时保留分页并可返回第一页', () => {
    pageMocks.search = 'page=999';
    pageMocks.useMovieList.mockReturnValue(
      movieListState({ data: { ...response, records: [], total: 10, page: 999 } }),
    );
    render(<MoviesPage />);

    expect(screen.getByText('暂无影片')).toBeInTheDocument();
    expect(screen.getByText('共 10 部影片')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '返回第一页' }));
    const nextParams = pageMocks.setSearchParams.mock.calls[0][0] as URLSearchParams;
    expect(nextParams.get('page')).toBeNull();
  });

  it('失败时展示 traceId 并允许手动重试', () => {
    const retry = vi.fn();
    pageMocks.useMovieList.mockReturnValue(
      movieListState({
        data: null,
        error: new ApiError('failed', {
          kind: 'HTTP',
          status: 500,
          traceId: 'trace-movies-1',
        }),
        retry,
      }),
    );
    render(<MoviesPage />);

    expect(screen.getByText('页面加载失败')).toBeInTheDocument();
    expect(screen.getByText('暂时无法加载影片，请稍后重试。')).toBeInTheDocument();
    expect(screen.getByText('问题编号：trace-movies-1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it('刷新失败时保留旧影片、来源和分页，并允许重试', () => {
    const retry = vi.fn();
    pageMocks.useMovieList.mockReturnValue(
      movieListState({
        error: new ApiError('服务端内部原文不得展示', {
          kind: 'HTTP',
          status: 500,
          traceId: 'trace-refresh-movies-1',
        }),
        retry,
      }),
    );
    render(<MoviesPage />);

    expect(screen.getByText('刷新失败，旧数据仍在展示')).toBeInTheDocument();
    expect(screen.getByText('影片刷新失败，旧数据仍在展示。')).toBeInTheDocument();
    expect(screen.getByText('问题编号：trace-refresh-movies-1')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '星河远征' })).toBeInTheDocument();
    expect(screen.getByText('演示数据')).toBeInTheDocument();
    expect(screen.getByText('共 1 部影片')).toBeInTheDocument();
    expect(screen.queryByText('服务端内部原文不得展示')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it('Mock、降级和过期来源说明在公共状态接入后仍保留', () => {
    pageMocks.useMovieList.mockReturnValue(
      movieListState({
        data: {
          ...response,
          isExpired: true,
        },
      }),
    );
    render(<MoviesPage />);

    expect(screen.queryByText('数据已过期，仅供参考')).not.toBeInTheDocument();
    expect(screen.getByText('当前为降级数据')).toBeInTheDocument();
    expect(screen.getByText('演示数据')).toBeInTheDocument();
  });

  it('离线时明确标记当前页面内存快照', () => {
    pageMocks.useMovieList.mockReturnValue(movieListState({ isOfflineSnapshot: true }));
    render(<MoviesPage />);

    expect(screen.getByText('数据待校验')).toBeInTheDocument();
    expect(screen.getByText('当前已离线，正在显示本页面内存中的只读快照。')).toBeInTheDocument();
  });
});
