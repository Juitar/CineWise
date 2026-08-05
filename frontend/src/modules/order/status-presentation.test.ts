import { describe, expect, it } from 'vitest';
import {
  ELECTRONIC_TICKET_STATUS_LABELS,
  ORDER_STATUS_LABELS,
  PAYMENT_STATUS_LABELS,
  REFUND_STATUS_LABELS,
} from './status-presentation';

describe('交易状态展示映射', () => {
  it('覆盖全部订单权威状态', () => {
    expect(ORDER_STATUS_LABELS).toEqual({
      PENDING_PAYMENT: '待支付',
      PAYING: '支付确认中',
      PAID: '已出票',
      CANCELLED: '已取消',
      EXPIRED: '已过期',
      REFUNDING: '退款处理中',
      REFUNDED: '已退款',
    });
  });

  it('覆盖支付、电子票和退款权威状态', () => {
    expect(PAYMENT_STATUS_LABELS).toEqual({
      INITIALIZED: '待处理',
      PROCESSING: '处理中',
      SUCCESS: '支付成功',
    });
    expect(ELECTRONIC_TICKET_STATUS_LABELS).toEqual({
      VALID: '有效',
      REFUNDED: '已退票',
      INVALIDATED: '已失效',
    });
    expect(REFUND_STATUS_LABELS).toEqual({
      REQUESTED: '已申请',
      PROCESSING: '处理中',
      SUCCESS: '退款成功',
    });
  });
});
