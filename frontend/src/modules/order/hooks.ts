import { useState, useCallback } from 'react';
import { createOrder } from './api';
import { isResultUnknownError, recoverCreatedOrder } from './recovery';
import type { CreateOrderRequest, OrderResponse } from './types';
import { ApiError } from '../../shared/api/ApiError';

export interface UseCreateOrderResult {
  loading: boolean;
  isRecovering: boolean;
  isResultUnknown: boolean;
  isSeatConflict: boolean;
  error: ApiError | null;
  order: OrderResponse | null;
  submitOrder: (
    request: CreateOrderRequest,
    idempotencyKey: string,
  ) => Promise<OrderResponse | null>;
  recoverOrder: (clientRequestId: string) => Promise<OrderResponse | null>;
  setResultUnknownState: (unknown: boolean) => void;
  resetError: () => void;
}

/**
 * 创建订单 Hook。
 *
 * 封装了：
 * 1. 正常的 POST /api/v1/orders 提交；
 * 2. 遭遇 RESULT_UNKNOWN（网络异常、超时、502/504）时，永远保持 isResultUnknown = true 状态，
 *    彻底禁用后续建单 POST，仅提供通过原 clientRequestId 调用 recoverOrder 的查询补偿；
 * 3. 对座位冲突（204001）的专门标识状态 isSeatConflict，方便确认页直接清理非法选中状态并刷新座位图；
 * 4. 401 未登录态复用 C 的全局处理器透传处理。
 */
export function useCreateOrder(): UseCreateOrderResult {
  const [loading, setLoading] = useState<boolean>(false);
  const [isRecovering, setIsRecovering] = useState<boolean>(false);
  const [isResultUnknown, setIsResultUnknown] = useState<boolean>(false);
  const [isSeatConflict, setIsSeatConflict] = useState<boolean>(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [order, setOrder] = useState<OrderResponse | null>(null);

  const resetError = useCallback(() => {
    setError(null);
    setIsSeatConflict(false);
  }, []);

  const setResultUnknownState = useCallback((unknown: boolean) => {
    setIsResultUnknown(unknown);
  }, []);

  const recoverOrder = useCallback(
    async (clientRequestId: string): Promise<OrderResponse | null> => {
      setIsRecovering(true);
      setError(null);
      try {
        const recovered = await recoverCreatedOrder(clientRequestId);
        if (recovered) {
          setOrder(recovered);
          setIsResultUnknown(false);
          return recovered;
        }
        const notFoundErr = new ApiError(
          '暂时无法查询到建单结果（可能尚未处理完毕或不存在），订单仍处于结果未知状态。请勿重复提交，可稍后重新查询。',
          { kind: 'NETWORK', status: 404, code: -1 },
        );
        setError(notFoundErr);
        return null;
      } catch (err: unknown) {
        const apiErr =
          err instanceof ApiError
            ? err
            : new ApiError(String(err), { kind: 'NETWORK', status: 500, code: -1 });
        setError(apiErr);
        return null;
      } finally {
        setIsRecovering(false);
      }
    },
    [],
  );

  const submitOrder = useCallback(
    async (request: CreateOrderRequest, idempotencyKey: string): Promise<OrderResponse | null> => {
      if (isResultUnknown) {
        return null;
      }
      setLoading(true);
      setIsRecovering(false);
      setIsSeatConflict(false);
      setError(null);

      try {
        const result = await createOrder(request, idempotencyKey);
        setOrder(result);
        return result;
      } catch (err: unknown) {
        // 判断是否属于 RESULT_UNKNOWN：此时服务端事务有可能已经完成，绝对不可重投 POST
        if (isResultUnknownError(err)) {
          setIsResultUnknown(true);
          setIsRecovering(true);
          try {
            const recovered = await recoverCreatedOrder(request.clientRequestId);
            if (recovered) {
              setOrder(recovered);
              setIsResultUnknown(false);
              return recovered;
            }
          } catch {
            // 如果等幂查询同样报错，保留原始未确认错误状态
          } finally {
            setIsRecovering(false);
          }
          const unknownErr = new ApiError(
            '提交建单未能确认服务端结果，已锁定为 RESULT_UNKNOWN 保护状态。禁止再次尝试 POST 提交，请按原请求标识查询结果。',
            { kind: 'NETWORK', status: 504, code: -1 },
          );
          setError(unknownErr);
          return null;
        }

        const apiErr =
          err instanceof ApiError
            ? err
            : new ApiError(String(err), { kind: 'HTTP', status: 500, code: -1 });
        if (apiErr.code === 204001) {
          setIsSeatConflict(true);
        }
        setError(apiErr);
        throw apiErr;
      } finally {
        setLoading(false);
      }
    },
    [isResultUnknown],
  );

  return {
    loading,
    isRecovering,
    isResultUnknown,
    isSeatConflict,
    error,
    order,
    submitOrder,
    recoverOrder,
    setResultUnknownState,
    resetError,
  };
}
