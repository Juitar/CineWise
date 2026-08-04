import { apiRequest } from '../../shared/api/client';
import type { CreateOrderRequest, OrderResponse } from './types';

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
