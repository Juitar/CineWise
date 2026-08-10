import { ApiError } from '../../shared/api/ApiError';
import { getOrderByRequestId } from './api';
import type { OrderResponse } from './types';

/**
 * 判定一个异常是否属于 RESULT_UNKNOWN（响应结果未知状态）。
 *
 * 分类规则：
 * 1. 满足 RESULT_UNKNOWN 的情况：
 *    - 写请求发生网络断开或请求超时（公共 API 抛出的 ApiError 携带 isResultUnknown: true）；
 *    - 代理层返回 502 Bad Gateway 或 504 Gateway Timeout（需要根据
 *      `status === 502 || status === 504` 显式识别），此时无法确认后端是否已经收到并执行事务；
 *    - 请求发往服务端但未收到合法确认响应包。
 * 2. 明确不得列入 RESULT_UNKNOWN 的情况：
 *    - HTTP 状态码为 400, 401, 403, 404, 409, 422；
 *    - 服务端明确返回的业务错误码（如 204001 座位不可锁定、205005 幂等键重放异常等）。
 *
 * @param error 捕获到的异常对象
 */
export function isResultUnknownError(error: unknown): boolean {
  if (error instanceof ApiError) {
    if (error.isResultUnknown) {
      return true;
    }
    if (error.status === 502 || error.status === 504) {
      return true;
    }
  }
  return false;
}

/**
 * 建单响应未知时的等幂查询补偿恢复。
 *
 * 【为什么绝不能自动重新发送 POST 写操作】：
 * 分布式高并发购票场景中，出现超时或网关报错（502/504）并不意味着服务端事务未执行。
 * 此时服务端可能已经将座位锁好并生成了订单，若前端自动发起新 POST 重投请求：
 * 1. 若没有幂等保护或使用新的标识，会导致重复抢单或锁定备用座位；
 * 2. 即便携带同一 Idempotency-Key，网关或中间件也容易因重复竞争触发锁冲突。
 * 因此按设计规范，必须严格使用原始生成的稳定 clientRequestId 发起等幂只读查询。
 *
 * @param clientRequestId 首笔建单申请生成的唯一客户端请求流水号
 * @returns 成功查询到本人已创建订单时返回 OrderResponse，未建单或未找到时返回 null
 */
export async function recoverCreatedOrder(clientRequestId: string): Promise<OrderResponse | null> {
  try {
    const order = await getOrderByRequestId(clientRequestId);
    return order;
  } catch (error: unknown) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}
