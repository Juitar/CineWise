import type { ElectronicTicketStatus, OrderStatus, PaymentStatus, RefundStatus } from './types';

export const ORDER_STATUS_LABELS: Readonly<Record<OrderStatus, string>> = {
  PENDING_PAYMENT: '待支付',
  PAYING: '支付确认中',
  PAID: '已出票',
  CANCELLED: '已取消',
  EXPIRED: '已过期',
  REFUNDING: '退款处理中',
  REFUNDED: '已退款',
};

export const PAYMENT_STATUS_LABELS: Readonly<Record<PaymentStatus, string>> = {
  INITIALIZED: '待处理',
  PROCESSING: '处理中',
  SUCCESS: '支付成功',
};

export const ELECTRONIC_TICKET_STATUS_LABELS: Readonly<Record<ElectronicTicketStatus, string>> = {
  VALID: '有效',
  REFUNDED: '已退票',
  INVALIDATED: '已失效',
};

export const REFUND_STATUS_LABELS: Readonly<Record<RefundStatus, string>> = {
  REQUESTED: '已申请',
  PROCESSING: '处理中',
  SUCCESS: '退款成功',
};
