import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestEnvironment } from '../../../features/test-utils';
import {
  useCancelOrder,
  useOrder,
  usePaymentQuery,
} from '../../../modules/order/transaction-hooks';

const routeMocks = vi.hoisted(() => ({ historyPush: vi.fn() }));
const travelMocks = vi.hoisted(() => ({ error: null, find: vi.fn(), isLoading: false }));

vi.mock('umi', () => ({
  history: { push: routeMocks.historyPush },
  useParams: () => ({ orderNo: 'CW1' }),
  Link: ({ to, children }: { to: string; children: React.ReactNode }) => (
    <a href={to}>{children}</a>
  ),
}));
vi.mock('../../../modules/order/transaction-hooks', () => ({
  useCancelOrder: vi.fn(),
  useOrder: vi.fn(),
  usePaymentQuery: vi.fn(),
}));
vi.mock('../../../modules/travel/useTravelTask', () => ({
  useTravelTaskByOrder: () => travelMocks,
}));
const contentMocks = vi.hoisted(() => ({
  getMovieDetail: vi.fn(),
  getCinemaDetail: vi.fn(),
}));

vi.mock('../../../modules/content/api', () => contentMocks);

import OrderDetailPage from './index';

setupTestEnvironment();

describe('订单详情交易时间展示', () => {
  beforeEach(() => {
    routeMocks.historyPush.mockReset();
    travelMocks.find.mockReset();
    travelMocks.error = null;
    contentMocks.getMovieDetail.mockReset();
    contentMocks.getCinemaDetail.mockReset();
    contentMocks.getMovieDetail.mockRejectedValue(new Error('内容夹具未提供影片资料'));
    contentMocks.getCinemaDetail.mockRejectedValue(new Error('内容夹具未提供影院资料'));
  });

  it('已支付订单按 orderId 查询真实出行任务后导航', async () => {
    travelMocks.find.mockResolvedValue({ taskId: '90001' });
    vi.mocked(useOrder).mockReturnValue({
      data: {
        orderId: '1',
        orderNo: 'CW1',
        showId: '11',
        movieId: '22',
        cinemaId: '33',
        showStartTime: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
        seatIds: ['101'],
        ticketCount: 1,
        unitPrice: '39.00',
        totalAmount: '39.00',
        status: 'PAID',
        expireTime: '2026-08-10T14:00:00+08:00',
        stateVersion: 1,
        updatedAt: '2026-08-05T04:00:00Z',
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    });
    vi.mocked(useCancelOrder).mockReturnValue({
      submitting: false,
      resultUnknown: false,
      error: null,
      submit: vi.fn(),
      recover: vi.fn(),
    });
    vi.mocked(usePaymentQuery).mockReturnValue({
      payment: null,
      querying: false,
      error: null,
      query: vi.fn(),
    });

    render(<OrderDetailPage />);
    const travelButton = await screen.findByRole('button', { name: '查看出行建议' });
    expect(screen.queryByText('场次编号：11')).not.toBeInTheDocument();
    expect(screen.queryByText(/座位编号[：:]/)).not.toBeInTheDocument();
    const breadcrumb = screen.getByRole('navigation', { name: '面包屑' });
    expect(within(breadcrumb).getByRole('link', { name: '个人中心' })).toHaveAttribute(
      'href',
      '/profile',
    );
    expect(within(breadcrumb).getByRole('link', { name: '我的订单' })).toHaveAttribute(
      'href',
      '/orders',
    );
    fireEvent.click(travelButton);
    await waitFor(() => expect(travelMocks.find).toHaveBeenCalledWith('1'));
    expect(routeMocks.historyPush).toHaveBeenCalledWith('/travel/90001');
  });

  it('开场前超过两小时的已支付订单不展示出行建议入口', () => {
    vi.mocked(useOrder).mockReturnValue({
      data: {
        orderId: '1',
        orderNo: 'CW1',
        showId: '11',
        movieId: '22',
        cinemaId: '33',
        showStartTime: '2099-08-10T14:30:00+08:00',
        seatIds: ['101'],
        ticketCount: 1,
        unitPrice: '39.00',
        totalAmount: '39.00',
        status: 'PAID',
        expireTime: '',
        stateVersion: 1,
        updatedAt: '2026-08-05T04:00:00Z',
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    });
    vi.mocked(useCancelOrder).mockReturnValue({
      submitting: false,
      resultUnknown: false,
      error: null,
      submit: vi.fn(),
      recover: vi.fn(),
    });
    vi.mocked(usePaymentQuery).mockReturnValue({
      payment: null,
      querying: false,
      error: null,
      query: vi.fn(),
    });

    render(<OrderDetailPage />);

    expect(screen.queryByRole('button', { name: '查看出行建议' })).not.toBeInTheDocument();
  });

  it('按固定业务时区展示支付截止和更新时间', async () => {
    vi.mocked(useOrder).mockReturnValue({
      data: {
        orderId: '1',
        orderNo: 'CW1',
        showId: '11',
        movieId: '22',
        cinemaId: '33',
        showStartTime: '2026-08-10T14:30:00+08:00',
        seatIds: ['101'],
        ticketCount: 1,
        unitPrice: '39.00',
        totalAmount: '39.00',
        status: 'PAID',
        expireTime: '2026-08-10T14:00:00+08:00',
        stateVersion: 1,
        updatedAt: '2026-08-05T04:00:00Z',
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    });
    vi.mocked(useCancelOrder).mockReturnValue({
      submitting: false,
      resultUnknown: false,
      error: null,
      submit: vi.fn(),
      recover: vi.fn(),
    });
    vi.mocked(usePaymentQuery).mockReturnValue({
      payment: null,
      querying: false,
      error: null,
      query: vi.fn(),
    });

    render(<OrderDetailPage />);

    expect(await screen.findByText('2026-08-10 14:00')).toBeInTheDocument();
    expect(screen.getByText('2026-08-05 12:00')).toBeInTheDocument();
    expect(screen.getByText('影院：影院信息暂不可用')).toBeInTheDocument();
    expect(screen.queryByText('2026-08-05T04:00:00Z')).not.toBeInTheDocument();
  });

  it('展示 D 提供的影片、影院和地址资料', async () => {
    contentMocks.getMovieDetail.mockResolvedValue({ title: '真实影片', posterUrl: null });
    contentMocks.getCinemaDetail.mockResolvedValue({
      name: '真实影院',
      area: '岳麓区',
      address: '测试路 1 号',
    });
    vi.mocked(useOrder).mockReturnValue({
      data: {
        orderId: '1',
        orderNo: 'CW1',
        showId: '11',
        movieId: '22',
        cinemaId: '33',
        showStartTime: '2026-08-10T14:30:00+08:00',
        seatIds: ['101'],
        ticketCount: 1,
        unitPrice: '39.00',
        totalAmount: '39.00',
        status: 'PAID',
        expireTime: '2026-08-10T14:00:00+08:00',
        stateVersion: 1,
        updatedAt: '2026-08-05T04:00:00Z',
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    });
    vi.mocked(useCancelOrder).mockReturnValue({
      submitting: false,
      resultUnknown: false,
      error: null,
      submit: vi.fn(),
      recover: vi.fn(),
    });
    vi.mocked(usePaymentQuery).mockReturnValue({
      payment: null,
      querying: false,
      error: null,
      query: vi.fn(),
    });

    render(<OrderDetailPage />);

    expect(await screen.findByText('真实影片')).toBeInTheDocument();
    expect(screen.getByText('影院：真实影院')).toBeInTheDocument();
    expect(screen.getByText('区域：岳麓区')).toBeInTheDocument();
    expect(screen.getByText('地址：测试路 1 号')).toBeInTheDocument();
  });

  it('影片影院资料首次加载期间持续显示骨架屏且不闪现兜底提示', async () => {
    let resolveMovie: (value: { title: string; posterUrl: null }) => void = () => undefined;
    let resolveCinema: (value: { name: string; area: string; address: string }) => void = () =>
      undefined;
    contentMocks.getMovieDetail.mockReturnValue(
      new Promise((resolve) => {
        resolveMovie = resolve;
      }),
    );
    contentMocks.getCinemaDetail.mockReturnValue(
      new Promise((resolve) => {
        resolveCinema = resolve;
      }),
    );
    vi.mocked(useOrder).mockReturnValue({
      data: {
        orderId: '1',
        orderNo: 'CW1',
        showId: '11',
        movieId: '22',
        cinemaId: '33',
        showStartTime: '2026-08-10T14:30:00+08:00',
        seatIds: ['101'],
        ticketCount: 1,
        unitPrice: '39.00',
        totalAmount: '39.00',
        status: 'PAID',
        expireTime: '2026-08-10T14:00:00+08:00',
        stateVersion: 1,
        updatedAt: '2026-08-05T04:00:00Z',
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    });
    vi.mocked(useCancelOrder).mockReturnValue({
      submitting: false,
      resultUnknown: false,
      error: null,
      submit: vi.fn(),
      recover: vi.fn(),
    });
    vi.mocked(usePaymentQuery).mockReturnValue({
      payment: null,
      querying: false,
      error: null,
      query: vi.fn(),
    });

    render(<OrderDetailPage />);

    expect(screen.getByLabelText('订单详情加载中')).toBeInTheDocument();
    expect(screen.queryByText('正在获取影片和影院信息')).not.toBeInTheDocument();
    expect(screen.queryByText('影片信息暂不可用')).not.toBeInTheDocument();
    expect(screen.queryByText('影院：影院信息暂不可用')).not.toBeInTheDocument();

    resolveMovie({ title: '真实影片', posterUrl: null });
    resolveCinema({ name: '真实影院', area: '岳麓区', address: '测试路 1 号' });

    expect(await screen.findByText('真实影片')).toBeInTheDocument();
    expect(screen.getByText('影院：真实影院')).toBeInTheDocument();
    expect(screen.queryByLabelText('订单详情加载中')).not.toBeInTheDocument();
  });
});
