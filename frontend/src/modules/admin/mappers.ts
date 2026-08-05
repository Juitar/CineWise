import type {
  AdminOrderDetailResponse,
  AdminOrderDetailSnapshot,
  AdminOrderPageResponse,
} from './types';

/** 把 REST 分页快照转换为展示模型，不补造内容或交易状态。 */
export function mapAdminOrderPage(response: AdminOrderPageResponse): AdminOrderPageResponse {
  return {
    ...response,
    records: response.records.map((record) => ({ ...record })),
  };
}

/** 将后端可能省略的可空关联统一为 null，简化只读组件的状态判断。 */
export function mapAdminOrderDetail(response: AdminOrderDetailResponse): AdminOrderDetailSnapshot {
  return {
    summary: { ...response.summary },
    paidTime: response.paidTime ?? null,
    cancelledTime: response.cancelledTime ?? null,
    refundedTime: response.refundedTime ?? null,
    seats: response.seats.map((seat) => ({ ...seat })),
    payment: response.payment
      ? { ...response.payment, paidAt: response.payment.paidAt ?? null }
      : null,
    ticket: response.ticket
      ? { ...response.ticket, invalidatedAt: response.ticket.invalidatedAt ?? null }
      : null,
    refund: response.refund
      ? {
          ...response.refund,
          processedAt: response.refund.processedAt ?? null,
          reason: response.refund.reason ?? null,
        }
      : null,
  };
}
