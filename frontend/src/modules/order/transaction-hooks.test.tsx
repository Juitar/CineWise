import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../shared/api/ApiError';
import { getAlternativeShows, getPayment, getRefund, getRefundImpact, payOrder } from './api';
import { getOrders } from './api';
import { markWriteResultUnknown } from './operation-session';
import { useOrders, usePaymentAction, usePaymentResult, useRefundPage } from './transaction-hooks';

vi.mock('./api', () => ({
  cancelOrder: vi.fn(),
  createRefund: vi.fn(),
  getAlternativeShows: vi.fn(),
  getElectronicTicket: vi.fn(),
  getOrder: vi.fn(),
  getOrders: vi.fn(),
  getPayment: vi.fn(),
  getRefund: vi.fn(),
  getRefundImpact: vi.fn(),
  payOrder: vi.fn(),
}));

const processingPayment = {
  orderId: '1',
  orderNo: 'CW1',
  paymentNo: 'PAY1',
  orderStatus: 'PAYING' as const,
  paymentStatus: 'PROCESSING' as const,
  ticketId: null,
  stateVersion: 1,
  updatedAt: '2026-08-05T09:00:00+08:00',
};

const successfulPayment = {
  ...processingPayment,
  orderStatus: 'PAID' as const,
  paymentStatus: 'SUCCESS' as const,
  ticketId: '2',
  stateVersion: 2,
};

const requestedRefund = {
  refundId: '1',
  refundNo: 'RF1',
  orderId: '1',
  orderNo: 'CW1',
  refundStatus: 'REQUESTED' as const,
  refundAmount: '39.00',
  orderStatus: 'REFUNDING' as const,
  ticketStatus: 'VALID' as const,
  stateVersion: 1,
  updatedAt: '2026-08-05T09:00:00+08:00',
};

const refundImpact = {
  orderId: '1',
  orderNo: 'CW1',
  refundAmount: '39.00',
  orderStatus: 'PAID' as const,
  ticketStatus: 'VALID' as const,
  showStartTime: '2026-08-10T14:30:00+08:00',
  orderVersion: 1,
  ticketVersion: 1,
  impactText: '退款影响以服务端规则为准。',
};

describe('订单交易 Hook 的结果未知恢复', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(payOrder).mockReset();
    vi.mocked(getPayment).mockReset();
    vi.mocked(getOrders).mockReset();
    vi.mocked(getAlternativeShows).mockReset();
    vi.mocked(getRefund).mockReset();
    vi.mocked(getRefundImpact).mockReset();
    vi.stubGlobal('crypto', { randomUUID: () => 'stable-payment-key' });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('支付响应未知后禁止第二次 POST，只允许查询原支付结果', async () => {
    vi.mocked(payOrder).mockRejectedValueOnce(
      new ApiError('响应未知', { kind: 'TIMEOUT', isResultUnknown: true }),
    );
    vi.mocked(getPayment).mockResolvedValueOnce(successfulPayment);
    const { result } = renderHook(() => usePaymentAction('CW1'));

    await act(async () => {
      await result.current.submit();
    });
    expect(result.current.resultUnknown).toBe(true);

    await act(async () => {
      await result.current.submit();
    });
    expect(payOrder).toHaveBeenCalledTimes(1);

    await act(async () => {
      await result.current.query();
    });
    expect(result.current.payment?.paymentStatus).toBe('SUCCESS');
    expect(result.current.resultUnknown).toBe(false);
  });

  it('支付自动查询达到 15 次上限后停止，卸载后不再发请求', async () => {
    vi.useFakeTimers();
    vi.mocked(getPayment).mockResolvedValue(processingPayment);
    const { unmount } = renderHook(() => usePaymentResult('CW1'));

    for (let pollIndex = 0; pollIndex < 15; pollIndex += 1) {
      await act(async () => {
        await vi.advanceTimersByTimeAsync(pollIndex === 0 ? 0 : 2_000);
      });
    }
    expect(getPayment).toHaveBeenCalledTimes(15);

    unmount();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(getPayment).toHaveBeenCalledTimes(15);
  });

  it('快速切换订单筛选时，忽略旧筛选条件的迟到响应', async () => {
    let resolvePendingOrders: (value: {
      total: number;
      page: number;
      size: number;
      records: [];
    }) => void;
    const pendingOrders = new Promise<{
      total: number;
      page: number;
      size: number;
      records: [];
    }>((resolve) => {
      resolvePendingOrders = resolve;
    });
    const paidOrders = { total: 0, page: 1, size: 10, records: [] };
    vi.mocked(getOrders).mockReturnValueOnce(pendingOrders).mockResolvedValueOnce(paidOrders);

    type OrderFilterProps = { status: 'PENDING_PAYMENT' | 'PAID' };
    const initialFilter: OrderFilterProps = { status: 'PENDING_PAYMENT' };
    const { result, rerender } = renderHook(
      ({ status }: OrderFilterProps) => useOrders({ page: 1, size: 10, status }),
      { initialProps: initialFilter },
    );
    rerender({ status: 'PAID' as const });

    await act(async () => {
      await Promise.resolve();
    });
    expect(result.current.data).toEqual(paidOrders);

    await act(async () => {
      resolvePendingOrders!({ total: 1, page: 1, size: 10, records: [] });
      await pendingOrders;
    });
    expect(result.current.data).toEqual(paidOrders);
  });

  it('首次查询到 REQUESTED 退款记录后清除结果未知保护', async () => {
    markWriteResultUnknown('refund', 'CW1');
    vi.mocked(getRefundImpact).mockResolvedValue(refundImpact);
    vi.mocked(getAlternativeShows).mockResolvedValue({ orderNo: 'CW1', shows: [] });
    vi.mocked(getRefund).mockResolvedValue(requestedRefund);

    const { result } = renderHook(() => useRefundPage('CW1'));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.refund?.refundStatus).toBe('REQUESTED');
    expect(result.current.resultUnknown).toBe(false);
    expect(sessionStorage.getItem('cinewise:refund:CW1')).toBeNull();
  });

  it('手动恢复查询到 PROCESSING 退款记录后清除结果未知保护', async () => {
    markWriteResultUnknown('refund', 'CW1');
    vi.mocked(getRefundImpact).mockResolvedValue(refundImpact);
    vi.mocked(getAlternativeShows).mockResolvedValue({ orderNo: 'CW1', shows: [] });
    vi.mocked(getRefund)
      .mockRejectedValueOnce(new ApiError('暂不可用', { kind: 'NETWORK', status: 503 }))
      .mockResolvedValueOnce({ ...requestedRefund, refundStatus: 'PROCESSING' });

    const { result } = renderHook(() => useRefundPage('CW1'));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.resultUnknown).toBe(true);

    await act(async () => {
      await result.current.recover();
    });
    expect(result.current.refund?.refundStatus).toBe('PROCESSING');
    expect(result.current.resultUnknown).toBe(false);
    expect(sessionStorage.getItem('cinewise:refund:CW1')).toBeNull();
  });
});
