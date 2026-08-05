export type AdminOrderStatus =
  'PENDING_PAYMENT' | 'PAYING' | 'PAID' | 'CANCELLED' | 'EXPIRED' | 'REFUNDING' | 'REFUNDED';

export type AdminPaymentStatus = 'INITIALIZED' | 'PROCESSING' | 'SUCCESS';
export type AdminTicketStatus = 'VALID' | 'REFUNDED' | 'INVALIDATED';
export type AdminRefundStatus = 'REQUESTED' | 'PROCESSING' | 'SUCCESS';

/** 后端管理订单列表支持的全部白名单条件。 */
export interface AdminOrderListQuery {
  orderNo?: string;
  userKeyword?: string;
  status?: AdminOrderStatus;
  movieId?: string;
  showId?: string;
  dateFrom?: string;
  dateTo?: string;
  page: number;
  size: number;
}

/** 管理订单列表最小摘要；未知状态保留为 string，由展示层安全降级。 */
export interface AdminOrderSummaryResponse {
  orderId: string;
  orderNo: string;
  userId: string;
  emailMasked: string | null;
  showId: string;
  movieId: string;
  cinemaId: string;
  showStartTime: string;
  ticketCount: number;
  unitPrice: string;
  totalAmount: string;
  orderStatus: AdminOrderStatus | string;
  expireTime: string;
  paymentStatus: AdminPaymentStatus | string | null;
  ticketStatus: AdminTicketStatus | string | null;
  refundStatus: AdminRefundStatus | string | null;
  stateVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminOrderPageResponse {
  total: number;
  page: number;
  size: number;
  records: AdminOrderSummaryResponse[];
}

export interface AdminSeatResponse {
  seatId: string;
  rowNo: string;
  seatNo: string;
  unitPrice: string;
}

export interface AdminPaymentResponse {
  paymentNo: string;
  amount: string;
  status: AdminPaymentStatus | string;
  requestedAt: string;
  paidAt?: string | null;
  stateVersion: number;
  updatedAt: string;
}

export interface AdminTicketResponse {
  ticketCode: string;
  status: AdminTicketStatus | string;
  issuedAt: string;
  invalidatedAt?: string | null;
  stateVersion: number;
  updatedAt: string;
}

export interface AdminRefundResponse {
  refundNo: string;
  reason?: string | null;
  status: AdminRefundStatus | string;
  requestedAt: string;
  processedAt?: string | null;
  stateVersion: number;
  updatedAt: string;
}

/** 可空时间和关联摘要可能被统一 JSON 策略省略，映射层会统一规范为 null。 */
export interface AdminOrderDetailResponse {
  summary: AdminOrderSummaryResponse;
  paidTime?: string | null;
  cancelledTime?: string | null;
  refundedTime?: string | null;
  seats: AdminSeatResponse[];
  payment?: AdminPaymentResponse | null;
  ticket?: AdminTicketResponse | null;
  refund?: AdminRefundResponse | null;
}

export type AdminPaymentSnapshot = Omit<AdminPaymentResponse, 'paidAt'> & {
  paidAt: string | null;
};
export type AdminTicketSnapshot = Omit<AdminTicketResponse, 'invalidatedAt'> & {
  invalidatedAt: string | null;
};
export type AdminRefundSnapshot = Omit<AdminRefundResponse, 'processedAt' | 'reason'> & {
  processedAt: string | null;
  reason: string | null;
};

/** 展示层使用的规范化详情快照；所有可省略关联在模块边界统一变为 null。 */
export interface AdminOrderDetailSnapshot {
  summary: AdminOrderSummaryResponse;
  paidTime: string | null;
  cancelledTime: string | null;
  refundedTime: string | null;
  seats: AdminSeatResponse[];
  payment: AdminPaymentSnapshot | null;
  ticket: AdminTicketSnapshot | null;
  refund: AdminRefundSnapshot | null;
}
