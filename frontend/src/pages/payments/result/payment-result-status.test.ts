import { describe, expect, it } from 'vitest';
import type { PaymentResponse } from '../../../modules/order/types';
import { resolvePaymentResultStatus } from './payment-result-status';

const basePayment: PaymentResponse = {
  orderId: '1',
  orderNo: 'CW1',
  paymentNo: 'PAY1',
  orderStatus: 'PAYING',
  paymentStatus: 'PROCESSING',
  ticketId: null,
  stateVersion: 1,
  updatedAt: '2026-08-05T10:00:00+08:00',
};

describe('支付结果状态映射', () => {
  it('保留取消、过期和退款的不同订单终态原因', () => {
    expect(
      resolvePaymentResultStatus({ ...basePayment, orderStatus: 'CANCELLED' }, false, false),
    ).toBe('CANCELLED');
    expect(
      resolvePaymentResultStatus({ ...basePayment, orderStatus: 'EXPIRED' }, false, false),
    ).toBe('EXPIRED');
    expect(
      resolvePaymentResultStatus({ ...basePayment, orderStatus: 'REFUNDED' }, false, false),
    ).toBe('REFUNDED');
  });

  it('响应丢失时优先进入只读结果查询状态', () => {
    expect(resolvePaymentResultStatus(basePayment, true, false)).toBe('RESULT_UNKNOWN');
  });
});
