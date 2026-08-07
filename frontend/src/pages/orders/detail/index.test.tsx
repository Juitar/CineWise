import React from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestEnvironment } from '../../../features/test-utils';
import {
  useCancelOrder,
  useOrder,
  usePaymentQuery,
} from '../../../modules/order/transaction-hooks';

vi.mock('umi', () => ({
  history: { push: vi.fn() },
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
const contentMocks = vi.hoisted(() => ({
  getMovieDetail: vi.fn(),
  getCinemaDetail: vi.fn(),
}));

vi.mock('../../../modules/content/api', () => contentMocks);

import OrderDetailPage from './index';

setupTestEnvironment();

describe('订单详情交易时间展示', () => {
  beforeEach(() => {
    contentMocks.getMovieDetail.mockReset();
    contentMocks.getCinemaDetail.mockReset();
    contentMocks.getMovieDetail.mockRejectedValue(new Error('内容夹具未提供影片资料'));
    contentMocks.getCinemaDetail.mockRejectedValue(new Error('内容夹具未提供影院资料'));
  });

  it('按固定业务时区展示支付截止和更新时间', () => {
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

    expect(screen.getByText('2026-08-10 14:00')).toBeInTheDocument();
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
});
