import type {
  AdminOrderDetailSnapshot,
  AdminOrderPageResponse,
  AdminOrderSummaryResponse,
  AdminOrderStatus,
  AdminPaymentStatus,
  AdminRefundStatus,
  AdminTicketStatus,
} from '../../modules/admin/types';

export type OrderStatus = AdminOrderStatus;
export type PaymentStatus = AdminPaymentStatus;
export type TicketStatus = AdminTicketStatus;
export type RefundStatus = AdminRefundStatus;

export type AdminOrderState =
  | 'LOADING'
  | 'EMPTY'
  | 'NORMAL'
  | 'FORBIDDEN'
  | 'QUERY_TOO_BROAD'
  | 'DIRECTORY_UNAVAILABLE'
  | 'GENERAL_ERROR';

export type AdminOrderSummaryView = AdminOrderSummaryResponse;
export type AdminOrderDetailView = AdminOrderDetailSnapshot;

export interface AdminOrderPageView<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}

/** 保留展示层原有泛型页模型，同时确保默认结构与 REST 分页契约一致。 */
export type AdminOrderDefaultPageView = AdminOrderPageResponse;
