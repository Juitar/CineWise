import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CinemaListState } from '../../modules/content/useCinemaList';
import { ApiError } from '../../shared/api/ApiError';
import type { CinemaSummary, ContentPageResponse } from '../../shared/types/api';

const pageMocks = vi.hoisted(() => ({
  search: '',
  setSearchParams: vi.fn(),
  useCinemaList: vi.fn(),
}));

vi.mock('umi', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
  useSearchParams: () => [new URLSearchParams(pageMocks.search), pageMocks.setSearchParams],
}));

vi.mock('../../modules/content/useCinemaList', () => ({
  useCinemaList: pageMocks.useCinemaList,
}));

import CinemasPage from './index';

const response: ContentPageResponse<CinemaSummary> = {
  records: [
    {
      cinemaId: '8200001',
      name: '妙语影城·滨江店',
      cityCode: '430100',
      area: '滨江区',
      address: '江南大道88号',
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

function cinemaListState(overrides: Partial<CinemaListState> = {}): CinemaListState {
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

describe('CinemasPage', () => {
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
    pageMocks.useCinemaList.mockReset();
    pageMocks.useCinemaList.mockReturnValue(cinemaListState());
  });

  it('使用后端影院响应渲染卡片和来源提示', () => {
    const { container } = render(<CinemasPage />);

    expect(screen.getByRole('heading', { level: 1, name: '影院列表' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '妙语影城·滨江店' })).toBeInTheDocument();
    expect(screen.getByText('滨江区')).toBeInTheDocument();
    expect(screen.getByText('江南大道88号')).toBeInTheDocument();
    expect(screen.getByText('演示数据')).toBeInTheDocument();
    expect(screen.getByText('共 1 家影院')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /妙语影城·滨江店/ })).toHaveAttribute(
      'href',
      '/cinemas/8200001',
    );
    expect(screen.queryByText('AI 推荐影院')).not.toBeInTheDocument();
    expect(container.querySelector('.cinema-list-address svg')).toBeInTheDocument();
    expect(container.querySelector('.cinema-list-arrow svg')).toBeInTheDocument();
    expect(container.textContent).not.toContain('📍');
    expect(pageMocks.useCinemaList).toHaveBeenCalledWith({
      keyword: undefined,
      location: '430100',
      page: 1,
      size: 20,
    });
  });

  it('提交关键词时保留城市并重置页码', () => {
    pageMocks.search = 'location=430100&page=3';
    render(<CinemasPage />);

    fireEvent.change(screen.getByRole('searchbox', { name: '搜索影院名称或地址' }), {
      target: { value: ' 滨江 ' },
    });
    fireEvent.click(screen.getByRole('button', { name: /搜\s*索/ }));

    const nextParams = pageMocks.setSearchParams.mock.calls[0][0] as URLSearchParams;
    expect(nextParams.get('location')).toBe('430100');
    expect(nextParams.get('keyword')).toBe('滨江');
    expect(nextParams.get('page')).toBeNull();
  });

  it('使用固定城市代码显示对应的中文城市名', () => {
    pageMocks.search = 'location=330100';
    render(<CinemasPage />);

    expect(screen.getByText('当前城市：杭州。影院基础信息来自后端内容服务。')).toBeInTheDocument();
    expect(pageMocks.useCinemaList).toHaveBeenCalledWith({
      keyword: undefined,
      location: '330100',
      page: 1,
      size: 20,
    });
  });

  it('加载、空结果和非法 URL 都有明确状态', () => {
    pageMocks.search = 'location=hangzhou&page=0';
    pageMocks.useCinemaList.mockReturnValue(cinemaListState({ data: null, isLoading: true }));
    const { rerender } = render(<CinemasPage />);

    expect(screen.getByLabelText('影院加载中')).toBeInTheDocument();
    expect(screen.getByText('部分地址参数无效，已使用安全默认值')).toBeInTheDocument();

    pageMocks.useCinemaList.mockReturnValue(
      cinemaListState({ data: { ...response, records: [], total: 0 } }),
    );
    rerender(<CinemasPage />);
    expect(screen.getByText('没有找到符合条件的影院')).toBeInTheDocument();
  });

  it('失败时显示 traceId 并允许手动重试', () => {
    const retry = vi.fn();
    pageMocks.useCinemaList.mockReturnValue(
      cinemaListState({
        data: null,
        error: new ApiError('failed', {
          kind: 'HTTP',
          status: 500,
          traceId: 'trace-cinemas-1',
        }),
        retry,
      }),
    );
    render(<CinemasPage />);

    expect(screen.getByText('影院加载失败')).toBeInTheDocument();
    expect(screen.getByText('问题编号：trace-cinemas-1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it('离线时标记当前页面内存快照', () => {
    pageMocks.useCinemaList.mockReturnValue(cinemaListState({ isOfflineSnapshot: true }));
    render(<CinemasPage />);

    expect(screen.getByText('当前已离线，正在显示本页面内存中的只读快照')).toBeInTheDocument();
  });
});
