import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CinemaDetailState } from '../../modules/content/useCinemaDetail';
import type { AvailableMoviesState } from '../../modules/ticketing/useAvailableMovies';
import { ApiError } from '../../shared/api/ApiError';

const mocks = vi.hoisted(() => ({
  cinemaId: '88001',
  useAvailableMovies: vi.fn(),
  useCinemaDetail: vi.fn(),
}));

vi.mock('umi', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
  useParams: () => ({ cinemaId: mocks.cinemaId }),
}));

vi.mock('../../modules/content/useCinemaDetail', () => ({
  useCinemaDetail: mocks.useCinemaDetail,
}));

vi.mock('../../modules/ticketing/useAvailableMovies', () => ({
  useAvailableMovies: mocks.useAvailableMovies,
}));

import CinemaDetailPage from './detail';

function cinemaState(overrides: Partial<CinemaDetailState> = {}): CinemaDetailState {
  return {
    data: {
      cinemaId: '88001',
      name: '长沙演示影城',
      cityCode: '430100',
      area: '岳麓区',
      address: '梅溪湖路 1 号',
      source: 'NETSTART_MAOYAN',
      sourceType: 'LIVE',
      dataTime: '2026-08-05T10:00:00+08:00',
      expiresAt: '2026-08-05T16:00:00+08:00',
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

function scheduleState(overrides: Partial<AvailableMoviesState> = {}): AvailableMoviesState {
  return {
    movies: [
      {
        movieId: '99001',
        title: '演示影片',
        posterUrl: null,
        showCount: 6,
        nearestStartTime: '2026-08-05T19:30:00+08:00',
        contentSource: 'NETSTART_MAOYAN',
        contentDataTime: '2026-08-05T09:00:00+08:00',
        scheduleSource: 'demo-seed',
        scheduleDataTime: '2026-08-05T10:00:00+08:00',
      },
    ],
    error: null,
    isLoading: false,
    retry: vi.fn(),
    ...overrides,
  };
}

describe('CinemaDetailPage', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    mocks.cinemaId = '88001';
    mocks.useCinemaDetail.mockReset();
    mocks.useAvailableMovies.mockReset();
    mocks.useCinemaDetail.mockReturnValue(cinemaState());
    mocks.useAvailableMovies.mockReturnValue(scheduleState());
  });

  it('展示真实影院资料和明确标识的演示排期并进入场次页', () => {
    render(<CinemaDetailPage />);

    expect(screen.getByRole('heading', { level: 1, name: '长沙演示影城' })).toBeInTheDocument();
    expect(screen.getByText('梅溪湖路 1 号')).toBeInTheDocument();
    expect(screen.getAllByText(/NETSTART_MAOYAN/)).toHaveLength(2);
    expect(screen.getByText('演示排期')).toBeInTheDocument();
    expect(screen.getByText(/影片资料：NETSTART_MAOYAN/)).toBeInTheDocument();
    expect(screen.getByText(/排期来源：demo-seed/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '选择影片' })).toHaveAttribute(
      'href',
      '/shows?movieId=99001&cinemaId=88001',
    );
    expect(screen.queryByText(/余座|起价/)).not.toBeInTheDocument();
  });

  it('排期失败时保留影院资料并允许单独重试', () => {
    const retry = vi.fn();
    mocks.useAvailableMovies.mockReturnValue(
      scheduleState({
        movies: [],
        error: new ApiError('failed', { kind: 'HTTP', status: 500, traceId: 'schedule-1' }),
        retry,
      }),
    );
    render(<CinemaDetailPage />);

    expect(screen.getByRole('heading', { level: 1, name: '长沙演示影城' })).toBeInTheDocument();
    expect(screen.getByText('影院资料已加载，但排期暂时不可用')).toBeInTheDocument();
    expect(screen.getByText('问题编号：schedule-1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重新加载排期' }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it('影院不存在时只显示返回列表入口', () => {
    mocks.useCinemaDetail.mockReturnValue(
      cinemaState({
        data: null,
        error: new ApiError('missing', { kind: 'HTTP', status: 404 }),
      }),
    );
    render(<CinemaDetailPage />);

    expect(screen.getByText('影院不存在或已下线')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '返回影院列表' })).toHaveAttribute('href', '/cinemas');
  });
});
