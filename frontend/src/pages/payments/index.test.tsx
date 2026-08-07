import React from 'react';
import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestEnvironment } from '../../features/test-utils';
import { useOrder, usePaymentAction } from '../../modules/order/transaction-hooks';

vi.mock('umi', () => ({
  history: { push: vi.fn() },
  useParams: () => ({ orderNo: 'CW1' }),
  Link: ({ to, children }: { to: string; children: React.ReactNode }) => (
    <a href={to}>{children}</a>
  ),
}));
vi.mock('../../modules/order/transaction-hooks', () => ({
  useOrder: vi.fn(),
  usePaymentAction: vi.fn(),
}));

import PaymentPage from './index';

setupTestEnvironment();

describe('支付页面场次上下文', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-10T05:59:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('复用订单详情查询展示开场时间', () => {
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
        status: 'PENDING_PAYMENT',
        expireTime: '2026-08-10T14:00:00+08:00',
        stateVersion: 1,
        updatedAt: '2026-08-05T12:00:00+08:00',
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    });
    vi.mocked(usePaymentAction).mockReturnValue({
      payment: null,
      submitting: false,
      querying: false,
      resultUnknown: false,
      error: null,
      submit: vi.fn(),
      query: vi.fn(),
    });

    render(<PaymentPage />);

    expect(screen.getByText('2026-08-10 14:30')).toBeInTheDocument();
    expect(screen.getByText('1分00秒')).toBeInTheDocument();
    expect(screen.queryByText('15分00秒')).not.toBeInTheDocument();
  });
});
