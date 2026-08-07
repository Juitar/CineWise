import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { MovieDetailState } from '../../modules/content/useMovieDetail';
import type { AvailableCinemasState } from '../../modules/ticketing/useAvailableCinemas';

const mocks = vi.hoisted(() => ({
  movieId: '10001',
  useAvailableCinemas: vi.fn(),
  useMovieDetail: vi.fn(),
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
  useParams: () => ({ movieId: mocks.movieId }),
}));

vi.mock('../../modules/ticketing/useAvailableCinemas', () => ({
  useAvailableCinemas: mocks.useAvailableCinemas,
}));

vi.mock('../../modules/content/useMovieDetail', () => ({
  useMovieDetail: mocks.useMovieDetail,
}));

import AvailableCinemasPage from './available-cinemas';

function state(overrides: Partial<AvailableCinemasState> = {}): AvailableCinemasState {
  return {
    data: {
      total: 1,
      page: 1,
      size: 20,
      records: [
        {
          cinemaId: '20001',
          name: '妙语影城',
          address: '岳麓大道 1 号',
          availableShowCount: 3,
          nearestStartTime: '2026-08-08T14:00:00+08:00',
          contentSource: 'LIVE',
          contentDataTime: '2026-08-06T09:00:00+08:00',
          contentExpiresAt: null,
          contentExpired: false,
          scheduleSource: 'demo-seed',
          scheduleDataTime: '2026-08-06T10:00:00+08:00',
        },
      ],
    },
    error: null,
    isLoading: false,
    isOfflineSnapshot: false,
    isRefreshing: false,
    retry: vi.fn(),
    ...overrides,
  };
}

function movieState(overrides: Partial<MovieDetailState> = {}): MovieDetailState {
  return {
    data: {
      movieId: '10001',
      title: '星河远征',
      posterUrl: null,
      genres: ['科幻'],
      durationMinutes: 120,
      rating: 8.8,
      summary: '影片简介',
      source: 'LIVE',
      sourceType: 'LIVE',
      dataTime: '2026-08-06T09:00:00+08:00',
      expiresAt: '2026-08-07T09:00:00+08:00',
      isExpired: false,
      degraded: false,
      fallbackType: null,
    },
    error: null,
    isLoading: false,
    retry: vi.fn(),
    ...overrides,
  };
}

describe('AvailableCinemasPage', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    mocks.movieId = '10001';
    mocks.useAvailableCinemas.mockReset();
    mocks.useMovieDetail.mockReset();
    mocks.useAvailableCinemas.mockReturnValue(state());
    mocks.useMovieDetail.mockReturnValue(movieState());
  });

  it('展示影院并仅跳转到既有场次页', () => {
    render(<AvailableCinemasPage />);

    expect(screen.getByRole('heading', { name: '妙语影城' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: '星河远征' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '选择妙语影城的场次' })).toHaveAttribute(
      'href',
      '/shows?movieId=10001&cinemaId=20001',
    );
  });

  it('任一业务 ID 无效时不构造只带一个参数的场次地址', () => {
    mocks.useAvailableCinemas.mockReturnValue(
      state({
        data: {
          ...state().data!,
          records: [{ ...state().data!.records[0], cinemaId: 'invalid-cinema-id' }],
        },
      }),
    );
    render(<AvailableCinemasPage />);

    expect(screen.queryByRole('link', { name: /选择.*场次/ })).not.toBeInTheDocument();
    expect(document.querySelector('a[href^="/shows?"]')).toBeNull();
  });

  it('空结果不展示购票入口，加载时显示骨架', () => {
    mocks.useAvailableCinemas.mockReturnValue(
      state({ data: { total: 0, page: 1, size: 20, records: [] } }),
    );
    const { rerender } = render(<AvailableCinemasPage />);
    expect(screen.getByText('当前没有可售影院')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /选择.*场次/ })).not.toBeInTheDocument();

    mocks.useAvailableCinemas.mockReturnValue(state({ data: null, isLoading: true }));
    rerender(<AvailableCinemasPage />);
    expect(screen.getByLabelText('可售影院加载中')).toBeInTheDocument();
  });

  it('失败、404 均可重试', () => {
    const retry = vi.fn();
    mocks.useAvailableCinemas.mockReturnValue(
      state({
        data: null,
        error: new ApiError('failed', {
          kind: 'HTTP',
          status: 503,
          code: 306003,
          traceId: 'cinema-1',
        }),
        retry,
      }),
    );
    const { rerender } = render(<AvailableCinemasPage />);
    expect(screen.getByText('可售场次暂时不可查询')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    expect(retry).toHaveBeenCalledOnce();

    const retry404 = vi.fn();
    mocks.useAvailableCinemas.mockReturnValue(
      state({
        data: null,
        error: new ApiError('missing', { kind: 'HTTP', status: 404 }),
        retry: retry404,
      }),
    );
    rerender(<AvailableCinemasPage />);
    expect(screen.getByText('可售影院加载失败')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    expect(retry404).toHaveBeenCalledOnce();
  });

  it('影片详情加载、404 和失败状态均独立于可售影院处理', () => {
    mocks.useMovieDetail.mockReturnValue(movieState({ data: null, isLoading: true }));
    const { rerender } = render(<AvailableCinemasPage />);
    expect(screen.getByLabelText('影片详情加载中')).toBeInTheDocument();

    const retry = vi.fn();
    mocks.useMovieDetail.mockReturnValue(
      movieState({
        data: null,
        error: new ApiError('missing', { kind: 'HTTP', status: 404 }),
        retry,
      }),
    );
    rerender(<AvailableCinemasPage />);
    expect(screen.getByText('影片不存在')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: /重\s*试/ })[0]);
    expect(retry).toHaveBeenCalledOnce();
  });

  it('明确提示过期、非实时来源、离线快照，并保持键盘可访问的链接', () => {
    mocks.useAvailableCinemas.mockReturnValue(
      state({
        isOfflineSnapshot: true,
        data: {
          ...state().data!,
          records: [{ ...state().data!.records[0], contentSource: 'MOCK', contentExpired: true }],
        },
      }),
    );
    render(<AvailableCinemasPage />);

    expect(screen.getByText('资料已过期')).toBeInTheDocument();
    expect(screen.getByText('非实时来源')).toBeInTheDocument();
    expect(screen.getByText('当前已离线，正在显示本页内存中的只读快照')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '选择妙语影城的场次' }).tagName).toBe('A');
  });
});
