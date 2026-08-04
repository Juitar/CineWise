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

/** 订单响应类型（对应建单、查询及详情结果） */
export interface OrderResponse {
  orderId: string;
  orderNo: string;
  showId: string;
  seatIds: string[];
  ticketCount: number;
  unitPrice: string;
  totalAmount: string;
  status: 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'REFUNDING' | 'REFUNDED' | 'EXPIRED';
  expireTime: string;
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
