import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { setupTestEnvironment } from '../../../features/test-utils';
import {
  useCancelOrder,
  useOrder,
  usePaymentQuery,
} from '../../../modules/order/transaction-hooks';

vi.mock('umi', () => ({
  history: { push: vi.fn() },
  useParams: () => ({ orderNo: 'CW1' }),
}));
vi.mock('../../../modules/order/transaction-hooks', () => ({
  useCancelOrder: vi.fn(),
  useOrder: vi.fn(),
  usePaymentQuery: vi.fn(),
}));

import OrderDetailPage from './index';

setupTestEnvironment();

describe('订单详情交易时间展示', () => {
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
    expect(screen.queryByText('2026-08-05T04:00:00Z')).not.toBeInTheDocument();
  });
});
