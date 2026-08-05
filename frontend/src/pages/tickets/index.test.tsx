import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { setupTestEnvironment } from '../../features/test-utils';
import { useElectronicTicket, useOrder } from '../../modules/order/transaction-hooks';

vi.mock('umi', () => ({ useParams: () => ({ ticketId: '99' }) }));
vi.mock('../../modules/order/transaction-hooks', () => ({
  useElectronicTicket: vi.fn(),
  useOrder: vi.fn(),
}));

import TicketPage from './index';

setupTestEnvironment();

describe('电子票页面场次上下文', () => {
  it('使用票据 orderNo 查询订单并展示权威开场时间', () => {
    vi.mocked(useElectronicTicket).mockReturnValue({
      data: {
        ticketId: '99',
        ticketCode: 'T99',
        orderId: '1',
        orderNo: 'CW1',
        showId: '11',
        seatIds: ['101'],
        status: 'VALID',
        qrPayload: 'local-ticket-payload',
        issuedAt: '2026-08-05T12:00:00+08:00',
        stateVersion: 1,
        updatedAt: '2026-08-05T12:00:00+08:00',
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
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
        updatedAt: '2026-08-05T12:00:00+08:00',
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    });

    render(<TicketPage />);

    expect(useOrder).toHaveBeenCalledWith('CW1');
    expect(screen.getByText('2026-08-10 14:30')).toBeInTheDocument();
    expect(screen.getByText('2026-08-05 12:00')).toBeInTheDocument();
    expect(screen.getByText('影片信息暂不可用')).toBeInTheDocument();
    expect(screen.queryByText(/场次时间请在/)).not.toBeInTheDocument();
  });
});
