import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CinemaListState } from '../../modules/content/useCinemaList';
import type { MovieListState } from '../../modules/content/useMovieList';
import { ApiError } from '../../shared/api/ApiError';
import type { CinemaSummary, ContentPageResponse, MovieSummary } from '../../shared/types/api';

const pageMocks = vi.hoisted(() => ({
  isMobile: true,
  navigate: vi.fn(),
  useCinemaList: vi.fn(),
  useMovieList: vi.fn(),
}));

vi.mock('umi', () => ({
  Link: ({ children, to, ...props }: React.PropsWithChildren<{ to: string }>) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useNavigate: () => pageMocks.navigate,
}));

vi.mock('../../shared/hooks/useMediaQuery', () => ({
  useMediaQuery: () => pageMocks.isMobile,
}));

vi.mock('../../modules/content/useMovieList', () => ({
  useMovieList: pageMocks.useMovieList,
}));

vi.mock('../../modules/content/useCinemaList', () => ({
  useCinemaList: pageMocks.useCinemaList,
}));

import HomePage from './index';

const movieResponse: ContentPageResponse<MovieSummary> = {
  records: [
    {
      movieId: '8100001',
      title: '星河远征',
      posterUrl: 'https://images.example.com/movie.webp',
      genres: ['科幻', '冒险'],
      durationMinutes: 128,
      rating: 8.6,
    },
  ],
  total: 1,
  page: 1,
  size: 5,
  source: 'NETSTART',
  sourceType: 'LIVE',
  dataTime: '2026-08-06T09:00:00+08:00',
  expiresAt: '2026-08-06T15:00:00+08:00',
  isExpired: false,
  degraded: false,
  fallbackType: null,
};

const cinemaResponse: ContentPageResponse<CinemaSummary> = {
  records: [
    {
      cinemaId: '8200001',
      name: '长沙星河影城',
      cityCode: '430100',
      area: '岳麓区',
      address: '梅溪湖路 88 号',
    },
  ],
  total: 1,
  page: 1,
  size: 3,
  source: 'DEMO_CONTENT',
  sourceType: 'MOCK',
  dataTime: '2026-08-05T09:00:00+08:00',
  expiresAt: '2026-08-05T15:00:00+08:00',
  isExpired: false,
  degraded: true,
  fallbackType: 'MOCK',
};

function movieState(overrides: Partial<MovieListState> = {}): MovieListState {
  return {
    data: movieResponse,
    error: null,
    isLoading: false,
    isOffline: false,
    isOfflineSnapshot: false,
    isRefreshing: false,
    retry: vi.fn(),
    ...overrides,
  };
}

function cinemaState(overrides: Partial<CinemaListState> = {}): CinemaListState {
  return {
    data: cinemaResponse,
    error: null,
    isLoading: false,
    isOffline: false,
    isOfflineSnapshot: false,
    isRefreshing: false,
    retry: vi.fn(),
    ...overrides,
  };
}

describe('HomePage', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    pageMocks.isMobile = true;
    pageMocks.useMovieList.mockReset();
    pageMocks.useCinemaList.mockReset();
    pageMocks.navigate.mockReset();
    pageMocks.useMovieList.mockReturnValue(movieState());
    pageMocks.useCinemaList.mockReturnValue(cinemaState());
  });

  it('使用内容 Hook 展示正常数据、真实来源、Mock 来源和更新时间', () => {
    render(<HomePage />);

    expect(screen.getByRole('heading', { level: 1, name: '妙语购票' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '正在热映' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '长沙影院' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: '星河远征' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: '长沙星河影城' })).toBeInTheDocument();
    expect(screen.getByText(/来源：NETSTART，更新于/)).toBeInTheDocument();
    expect(screen.getByText('演示数据')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看《星河远征》详情并选择影院' })).toHaveAttribute(
      'href',
      '/movies/8100001',
    );
    expect(screen.getByRole('link', { name: '查看长沙星河影城详情' })).toHaveAttribute(
      'href',
      '/cinemas/8200001',
    );
    expect(pageMocks.useMovieList).toHaveBeenCalledWith({ page: 1, size: 5 });
    expect(pageMocks.useCinemaList).toHaveBeenCalledWith({
      location: '430100',
      page: 1,
      size: 3,
    });
  });

  it('影片和影院首次加载时分别显示骨架状态', () => {
    pageMocks.useMovieList.mockReturnValue(movieState({ data: null, isLoading: true }));
    pageMocks.useCinemaList.mockReturnValue(cinemaState({ data: null, isLoading: true }));
    const { container } = render(<HomePage />);

    expect(screen.getByLabelText('首页影片加载中')).toBeInTheDocument();
    expect(screen.getByLabelText('首页影院加载中')).toBeInTheDocument();
    expect(container.querySelector('.adm-skeleton')).toBeInTheDocument();
  });

  it('保留旧影片时展示刷新和离线只读快照状态', () => {
    pageMocks.useMovieList.mockReturnValue(
      movieState({ isOfflineSnapshot: true, isRefreshing: true }),
    );
    render(<HomePage />);

    expect(screen.getByText('正在更新影片…')).toBeInTheDocument();
    expect(screen.getByText('当前已离线，正在显示本页面内存中的影片只读快照')).toBeInTheDocument();
    expect(screen.getByTestId('home-movie-8100001')).toBeInTheDocument();
  });

  it('保留旧影院时展示刷新和离线只读快照状态', () => {
    pageMocks.useCinemaList.mockReturnValue(
      cinemaState({ isOfflineSnapshot: true, isRefreshing: true }),
    );
    render(<HomePage />);

    expect(screen.getByText('正在更新影院…')).toBeInTheDocument();
    expect(screen.getByText('当前已离线，正在显示本页面内存中的影院只读快照')).toBeInTheDocument();
    expect(screen.getByTestId('home-cinema-8200001')).toBeInTheDocument();
  });

  it('影片和影院空结果都有明确提示', () => {
    pageMocks.useMovieList.mockReturnValue(
      movieState({ data: { ...movieResponse, records: [], total: 0 } }),
    );
    pageMocks.useCinemaList.mockReturnValue(
      cinemaState({ data: { ...cinemaResponse, records: [], total: 0 } }),
    );
    const { container } = render(<HomePage />);

    expect(screen.getByText('暂无可展示的影片')).toBeInTheDocument();
    expect(screen.getByText('长沙暂无可展示的影院')).toBeInTheDocument();
    expect(container.querySelectorAll('.adm-empty')).toHaveLength(2);
  });

  it('网络失败时显示问题编号并可分别重试', () => {
    const retryMovies = vi.fn();
    const retryCinemas = vi.fn();
    pageMocks.useMovieList.mockReturnValue(
      movieState({
        data: null,
        error: new ApiError('offline', {
          kind: 'NETWORK',
          traceId: 'trace-home-movies',
        }),
        retry: retryMovies,
      }),
    );
    pageMocks.useCinemaList.mockReturnValue(
      cinemaState({
        data: null,
        error: new ApiError('offline', {
          kind: 'NETWORK',
          traceId: 'trace-home-cinemas',
        }),
        retry: retryCinemas,
      }),
    );
    const { container } = render(<HomePage />);

    expect(screen.getByText('影片加载失败')).toBeInTheDocument();
    expect(screen.getByText('影院加载失败')).toBeInTheDocument();
    expect(screen.getByText('问题编号：trace-home-movies')).toBeInTheDocument();
    expect(screen.getByText('问题编号：trace-home-cinemas')).toBeInTheDocument();
    expect(container.querySelectorAll('.adm-error-block')).toHaveLength(2);
    const retryButtons = screen.getAllByRole('button', { name: /重\s*试/ });
    fireEvent.click(retryButtons[0]);
    fireEvent.click(retryButtons[1]);
    expect(retryMovies).toHaveBeenCalledOnce();
    expect(retryCinemas).toHaveBeenCalledOnce();
  });

  it('海报加载失败后显示占位', () => {
    render(<HomePage />);

    fireEvent.error(screen.getByRole('img', { name: '星河远征海报' }));
    expect(screen.getByLabelText('星河远征暂无海报')).toBeInTheDocument();
  });

  it('真实内容区不出现杭州、演示距离、价格、库存、场次时间或购票入口', () => {
    const { container } = render(<HomePage />);
    const content = container.querySelector('.home-page-content');
    expect(content).not.toBeNull();
    const text = content?.textContent ?? '';
    const cardText = Array.from(
      content?.querySelectorAll('.movie-card, .cinema-card') ?? [],
      (card) => card.textContent ?? '',
    ).join(' ');

    expect(text).not.toContain('杭州');
    expect(text).not.toContain('演示距离');
    expect(text).not.toContain('km');
    expect(text).not.toContain('¥');
    expect(text).not.toContain('库存');
    expect(cardText).not.toMatch(/\b\d{1,2}:\d{2}\b/);
    expect(within(content as HTMLElement).queryByRole('button', { name: '购票' })).toBeNull();
    expect(within(content as HTMLElement).queryByRole('link', { name: /购票/ })).toBeNull();
  });

  it.each([
    ['移动端', true],
    ['PC 端', false],
  ])('%s 都展示影片和影院基础资料', (_name, isMobile) => {
    pageMocks.isMobile = isMobile;
    render(<HomePage />);

    expect(screen.getByTestId('home-movie-8100001')).toBeInTheDocument();
    expect(screen.getByTestId('home-cinema-8200001')).toBeInTheDocument();
  });

  it('首页 Agent 输入只保存内存草稿并跳转受保护工作区', () => {
    const { container } = render(<HomePage />);
    const agentInput = screen.getByLabelText('首页 Agent 输入');
    expect(agentInput).toHaveClass('adm-input-element');
    expect(container.querySelector('.home-mobile-agent-input')).toHaveClass('adm-input');
    expect(container.querySelector('.home-mobile-agent-send-btn')).toHaveClass('adm-button');
    fireEvent.change(agentInput, {
      target: { value: '推荐一部电影' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    expect(pageMocks.navigate).toHaveBeenCalledWith('/assistant');
  });

  it('PC 首页继续使用 Ant Design 的加载、错误和空态组件', () => {
    pageMocks.isMobile = false;
    pageMocks.useMovieList.mockReturnValue(movieState({ data: null, isLoading: true }));
    pageMocks.useCinemaList.mockReturnValue(
      cinemaState({ data: { ...cinemaResponse, records: [], total: 0 } }),
    );
    const { container } = render(<HomePage />);

    expect(container.querySelector('.ant-skeleton')).toBeInTheDocument();
    expect(container.querySelector('.ant-empty')).toBeInTheDocument();
    expect(container.querySelector('.adm-skeleton')).not.toBeInTheDocument();
  });
});
