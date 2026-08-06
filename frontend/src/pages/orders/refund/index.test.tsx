import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { setupTestEnvironment } from '../../../features/test-utils';
import { useRefundPage } from '../../../modules/order/transaction-hooks';
import { ApiError } from '../../../shared/api/ApiError';

vi.mock('umi', () => ({
  history: { push: vi.fn() },
  useParams: () => ({ orderNo: 'CW1' }),
}));
vi.mock('../../../modules/order/transaction-hooks', () => ({
  useRefundPage: vi.fn(),
}));

import RefundPage from './index';

setupTestEnvironment();

describe('退款页面时间展示', () => {
  it('将服务端 ISO 时间统一格式化后传给退款摘要和替代场次', () => {
    vi.mocked(useRefundPage).mockReturnValue({
      impact: {
        orderId: '1',
        orderNo: 'CW1',
        refundAmount: '39.90',
        orderStatus: 'PAID',
        ticketStatus: 'VALID',
        showStartTime: '2026-08-10T14:30:00+08:00',
        orderVersion: 2,
        ticketVersion: 1,
        impactText: '退票后电子票将失效。',
      },
      alternatives: {
        orderNo: 'CW1',
        shows: [
          {
            showId: '11',
            movieId: '22',
            cinemaId: '33',
            startTime: '2026-08-11T19:00:00+08:00',
            basePrice: '39.90',
            status: 'ON_SALE',
            availableSeatCount: 80,
          },
        ],
      },
      refund: null,
      loading: false,
      submitting: false,
      resultUnknown: false,
      error: null,
      alternativeError: null,
      submit: vi.fn(),
      recover: vi.fn(),
    });

    render(<RefundPage />);

    expect(screen.getByText('2026-08-10 14:30')).toBeInTheDocument();
    expect(screen.getByText('2026-08-11 19:00')).toBeInTheDocument();
    expect(screen.queryByText('2026-08-10T14:30:00+08:00')).not.toBeInTheDocument();
  });

  it('退款影响查询返回 205006 时展示不可退状态而非零金额表单', () => {
    vi.mocked(useRefundPage).mockReturnValue({
      impact: null,
      alternatives: null,
      refund: null,
      loading: false,
      submitting: false,
      resultUnknown: false,
      error: new ApiError('订单不可退', {
        kind: 'HTTP',
        code: 205006,
        status: 409,
      }),
      alternativeError: null,
      submit: vi.fn(),
      recover: vi.fn(),
    });

    render(<RefundPage />);

    expect(screen.getByText('当前订单不可退票')).toBeInTheDocument();
    expect(screen.queryByText('¥ 0.00')).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });
});
