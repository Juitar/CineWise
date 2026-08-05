import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../shared/api/ApiError';
import { getPayment, payOrder } from './api';
import { usePaymentAction, usePaymentResult } from './transaction-hooks';

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

describe('订单交易 Hook 的结果未知恢复', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(payOrder).mockReset();
    vi.mocked(getPayment).mockReset();
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
});
