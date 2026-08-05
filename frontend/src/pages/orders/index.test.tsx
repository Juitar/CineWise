import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { setupTestEnvironment } from '../../features/test-utils';
import { useOrders } from '../../modules/order/transaction-hooks';

vi.mock('umi', () => ({ history: { push: vi.fn() } }));
vi.mock('../../modules/order/transaction-hooks', () => ({ useOrders: vi.fn() }));

import OrdersPage from './index';

setupTestEnvironment();

describe('个人订单列表场次上下文', () => {
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
});
