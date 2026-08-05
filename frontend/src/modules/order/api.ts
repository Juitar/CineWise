import { apiRequest } from '../../shared/api/client';
import type {
  AlternativeShowsResponse,
  CreateOrderRequest,
  CreateRefundRequest,
  ElectronicTicketResponse,
  OrderPageResponse,
  OrderQuery,
  OrderQueryResponse,
  OrderResponse,
  PaymentResponse,
  RefundImpactResponse,
  RefundResponse,
} from './types';

/**
 * 原子锁座并创建待支付订单
 * @param request 建单请求体（含 showId, seatIds, clientRequestId，且 ID 必须为 string）
 * @param idempotencyKey 幂等键头，必须为稳定 uuid，相同标识重放返回原结果
 */
export async function createOrder(
  request: CreateOrderRequest,
  idempotencyKey: string,
): Promise<OrderResponse> {
  return apiRequest<OrderResponse>('/api/v1/orders', {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
    body: request,
  });
}

/**
 * 按客户端请求标识查询和恢复本人的建单结果（用于 RESULT_UNKNOWN 时等幂恢复，禁止发起新 POST）
 * @param clientRequestId 原申请建单时生成的唯一请求流水号
 */
export async function getOrderByRequestId(clientRequestId: string): Promise<OrderResponse> {
  return apiRequest<OrderResponse>(
    `/api/v1/orders/by-request/${encodeURIComponent(clientRequestId)}`,
    {
      method: 'GET',
    },
  );
}

/** 查询当前用户订单分页，筛选条件全部交给服务端执行。 */
export async function getOrders(query: OrderQuery): Promise<OrderPageResponse> {
  return apiRequest<OrderPageResponse>('/api/v1/orders', {
    method: 'GET',
    query: { ...query },
  });
}

/** 查询当前用户拥有的订单详情。 */
export async function getOrder(orderNo: string): Promise<OrderQueryResponse> {
  return apiRequest<OrderQueryResponse>(`/api/v1/orders/${encodeURIComponent(orderNo)}`, {
    method: 'GET',
  });
}

/**
 * 取消待支付订单。
 *
 * 写响应未知时调用方只能使用 getOrder 查询服务端状态，不能自动重发本请求。
 */
export async function cancelOrder(orderNo: string, idempotencyKey: string): Promise<OrderResponse> {
  return apiRequest<OrderResponse>(`/api/v1/orders/${encodeURIComponent(orderNo)}/cancel`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

/**
 * 发起 Mock 支付，业务请求体保持为空。
 *
 * 六位模拟密码只能在浏览器组件内校验，不得传入本函数或任何请求字段。
 */
export async function payOrder(orderNo: string, idempotencyKey: string): Promise<PaymentResponse> {
  return apiRequest<PaymentResponse>(`/api/v1/orders/${encodeURIComponent(orderNo)}/payments`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

/** 支付响应未知时查询当前订单的权威支付记录。 */
export async function getPayment(orderNo: string): Promise<PaymentResponse> {
  return apiRequest<PaymentResponse>(`/api/v1/orders/${encodeURIComponent(orderNo)}/payment`, {
    method: 'GET',
  });
}

/** 查询当前用户拥有的电子票。 */
export async function getElectronicTicket(ticketId: string): Promise<ElectronicTicketResponse> {
  return apiRequest<ElectronicTicketResponse>(`/api/v1/tickets/${encodeURIComponent(ticketId)}`, {
    method: 'GET',
  });
}

/** 查询退票二次确认所需的服务端权威影响摘要。 */
export async function getRefundImpact(orderNo: string): Promise<RefundImpactResponse> {
  return apiRequest<RefundImpactResponse>(
    `/api/v1/orders/${encodeURIComponent(orderNo)}/refund-confirmation`,
    { method: 'POST' },
  );
}

/**
 * 提交传统页面退票请求。
 *
 * request 不包含 actionId；响应未知时调用方只能查询原退款结果。
 */
export async function createRefund(
  orderNo: string,
  request: CreateRefundRequest,
  idempotencyKey: string,
): Promise<RefundResponse> {
  return apiRequest<RefundResponse>(`/api/v1/orders/${encodeURIComponent(orderNo)}/refunds`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: request,
  });
}

/** 退票响应未知时查询原订单的权威退款结果。 */
export async function getRefund(orderNo: string): Promise<RefundResponse> {
  return apiRequest<RefundResponse>(`/api/v1/orders/${encodeURIComponent(orderNo)}/refund`, {
    method: 'GET',
  });
}

/** 查询同影片替代场次；余座仅用于展示，进入选座页后必须刷新座位图。 */
export async function getAlternativeShows(
  orderNo: string,
  dateFrom?: string,
  dateTo?: string,
): Promise<AlternativeShowsResponse> {
  return apiRequest<AlternativeShowsResponse>(
    `/api/v1/orders/${encodeURIComponent(orderNo)}/alternative-shows`,
    { method: 'GET', query: { dateFrom, dateTo } },
  );
}
