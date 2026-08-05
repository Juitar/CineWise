/**
 * 订单模块通用类型定义（建单、支付、电子票、退票与替代场次）
 * 注意：所有业务 ID 与金额均以 string 保存。
 */

/** 创建订单请求参数 */
export interface CreateOrderRequest {
  showId: string;
  seatIds: string[];
  clientRequestId: string;
}

/** 订单写操作响应类型；查询接口的场次上下文由 OrderQueryResponse 扩展。 */
export interface OrderResponse {
  orderId: string;
  orderNo: string;
  showId: string;
  seatIds: string[];
  ticketCount: number;
  unitPrice: string;
  totalAmount: string;
  status: OrderStatus;
  expireTime: string;
  stateVersion: number;
  updatedAt: string;
}

/** 个人订单列表与详情查询返回的权威场次上下文。 */
export interface OrderQueryResponse extends OrderResponse {
  movieId: string;
  cinemaId: string;
  showStartTime: string;
}

export type OrderStatus =
  'PENDING_PAYMENT' | 'PAYING' | 'PAID' | 'CANCELLED' | 'EXPIRED' | 'REFUNDING' | 'REFUNDED';

export interface OrderPageResponse {
  total: number;
  page: number;
  size: number;
  records: OrderQueryResponse[];
}

export interface OrderQuery {
  orderNo?: string;
  status?: OrderStatus;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}

export type PaymentStatus = 'INITIALIZED' | 'PROCESSING' | 'SUCCESS';

export interface PaymentResponse {
  orderId: string;
  orderNo: string;
  paymentNo: string;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus;
  ticketId: string | null;
  stateVersion: number;
  updatedAt: string;
}

export type ElectronicTicketStatus = 'VALID' | 'REFUNDED' | 'INVALIDATED';

export interface ElectronicTicketResponse {
  ticketId: string;
  ticketCode: string;
  orderId: string;
  orderNo: string;
  showId: string;
  seatIds: string[];
  status: ElectronicTicketStatus;
  qrPayload: string;
  issuedAt: string;
  stateVersion: number;
  updatedAt: string;
}

export interface RefundImpactResponse {
  orderId: string;
  orderNo: string;
  refundAmount: string;
  orderStatus: OrderStatus;
  ticketStatus: ElectronicTicketStatus;
  showStartTime: string;
  orderVersion: number;
  ticketVersion: number;
  impactText: string;
}

export type RefundStatus = 'REQUESTED' | 'PROCESSING' | 'SUCCESS';

export interface CreateRefundRequest {
  refundReason?: string;
  clientRequestId: string;
}

export interface RefundResponse {
  refundId: string;
  refundNo: string;
  orderId: string;
  orderNo: string;
  refundStatus: RefundStatus;
  refundAmount: string;
  orderStatus: OrderStatus;
  ticketStatus: ElectronicTicketStatus;
  stateVersion: number;
  updatedAt: string;
}

/**
 * 替代场次候选对象契约（按新规范已包含 movieId 字段）
 * 注：当前后台主干 dev 尚未合并该契约分支，本类型为契约预置与 Mock 定义，第一批不依赖实体调用。
 */
export interface AlternativeShow {
  showId: string;
  movieId: string;
  cinemaId: string;
  startTime: string;
  basePrice: string;
  status: 'ON_SALE' | 'OFF_SALE' | 'SOLD_OUT';
  availableSeatCount: number;
}

export interface AlternativeShowsResponse {
  orderNo: string;
  shows: AlternativeShow[];
}
