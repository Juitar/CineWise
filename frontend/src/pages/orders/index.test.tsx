import React from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestEnvironment } from '../../features/test-utils';
import { useOrders } from '../../modules/order/transaction-hooks';

vi.mock('umi', () => ({ history: { push: vi.fn() } }));
vi.mock('../../modules/order/transaction-hooks', () => ({ useOrders: vi.fn() }));
const contentMocks = vi.hoisted(() => ({
  getMovieDetail: vi.fn(),
  getCinemaDetail: vi.fn(),
}));

vi.mock('../../modules/content/api', () => contentMocks);

import OrdersPage from './index';

setupTestEnvironment();

describe('个人订单列表场次上下文', () => {
  beforeEach(() => {
    contentMocks.getMovieDetail.mockReset();
    contentMocks.getCinemaDetail.mockReset();
    contentMocks.getMovieDetail.mockRejectedValue(new Error('内容夹具未提供影片资料'));
    contentMocks.getCinemaDetail.mockRejectedValue(new Error('内容夹具未提供影院资料'));
  });

  it('展示权威开场时间并保留下单日期筛选语义', () => {
    vi.mocked(useOrders).mockReturnValue({
      data: {
        total: 1,
        page: 1,
        size: 10,
        records: [
          {
            orderId: '1',
            orderNo: 'CW1',
            showId: '11',
            movieId: '22',
            cinemaId: '33',
            showStartTime: '2026-08-10T06:30:00Z',
            seatIds: ['101'],
            ticketCount: 1,
            unitPrice: '39.00',
            totalAmount: '39.00',
            status: 'PAID',
            expireTime: '2026-08-10T06:00:00Z',
            stateVersion: 1,
            updatedAt: '2026-08-05T12:00:00+08:00',
          },
        ],
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    });

    render(<OrdersPage />);

    expect(screen.getByText(/场次：2026-08-10 14:30/)).toBeInTheDocument();
    expect(screen.getByText('影片信息暂不可用')).toBeInTheDocument();
    expect(screen.getByText(/场次编号：11/)).toBeInTheDocument();
    expect(screen.getByLabelText('按下单日期筛选订单')).toBeInTheDocument();
    expect(screen.getByText('下单日期：')).toBeInTheDocument();
    expect(screen.queryByText(/场次时间请在/)).not.toBeInTheDocument();
  });

  it('展示 D 提供的影片和影院资料，不伪造内容字段', async () => {
    contentMocks.getMovieDetail.mockResolvedValue({ title: '真实影片', posterUrl: null });
    contentMocks.getCinemaDetail.mockResolvedValue({
      name: '真实影院',
      area: '岳麓区',
      address: '测试路 1 号',
    });
    vi.mocked(useOrders).mockReturnValue({
      data: {
        total: 1,
        page: 1,
        size: 10,
        records: [
          {
            orderId: '1',
            orderNo: 'CW1',
            showId: '11',
            movieId: '22',
            cinemaId: '33',
            showStartTime: '2026-08-10T06:30:00Z',
            seatIds: ['101'],
            ticketCount: 1,
            unitPrice: '39.00',
            totalAmount: '39.00',
            status: 'PAID',
            expireTime: '2026-08-10T06:00:00Z',
            stateVersion: 1,
            updatedAt: '2026-08-05T12:00:00+08:00',
          },
        ],
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    });

    render(<OrdersPage />);

    expect(await screen.findByText('真实影片')).toBeInTheDocument();
    expect(screen.getByText('影院：真实影院')).toBeInTheDocument();
    expect(screen.getByText('区域：岳麓区')).toBeInTheDocument();
    expect(screen.getByText('地址：测试路 1 号')).toBeInTheDocument();
  });
});
