import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../shared/api/ApiError';
import {
  cancelOrder,
  createRefund,
  getAlternativeShows,
  getElectronicTicket,
  getOrder,
  getOrders,
  getPayment,
  getRefund,
  getRefundImpact,
  payOrder,
} from './api';
import {
  clearWriteOperationSession,
  getWriteOperationSession,
  markWriteResultUnknown,
} from './operation-session';
import { isResultUnknownError } from './recovery';
import type {
  AlternativeShowsResponse,
  ElectronicTicketResponse,
  OrderPageResponse,
  OrderQuery,
  OrderQueryResponse,
  OrderResponse,
  PaymentResponse,
  RefundImpactResponse,
  RefundResponse,
} from './types';

function toApiError(error: unknown): ApiError {
  return error instanceof ApiError
    ? error
    : new ApiError('请求处理失败', { kind: 'NETWORK', status: 500 });
}

export interface QueryState<T> {
  data: T | null;
  loading: boolean;
  error: ApiError | null;
  refresh: () => Promise<T | null>;
}

/** 查询当前用户的订单分页，筛选变化时丢弃旧响应。 */
export function useOrders(query: OrderQuery): QueryState<OrderPageResponse> {
  const [data, setData] = useState<OrderPageResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const latestRequestId = useRef(0);
  const queryKey = JSON.stringify(query);

  const refresh = useCallback(async (): Promise<OrderPageResponse | null> => {
    const requestId = latestRequestId.current + 1;
    latestRequestId.current = requestId;
    setLoading(true);
    setError(null);
    try {
      const result = await getOrders(query);
      // 筛选条件快速变化时，旧请求后返回也不能覆盖最新订单列表。
      if (requestId === latestRequestId.current) {
        setData(result);
      }
      return result;
    } catch (requestError: unknown) {
      if (requestId === latestRequestId.current) {
        setError(toApiError(requestError));
      }
      return null;
    } finally {
      if (requestId === latestRequestId.current) {
        setLoading(false);
      }
    }
    // queryKey keeps the callback stable for equivalent filter objects.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryKey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { data, loading, error, refresh };
}

/** 查询当前用户拥有的订单详情。 */
export function useOrder(orderNo: string): QueryState<OrderQueryResponse> {
  const [data, setData] = useState<OrderQueryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const refresh = useCallback(async (): Promise<OrderQueryResponse | null> => {
    if (!orderNo) {
      setLoading(false);
      return null;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await getOrder(orderNo);
      setData(result);
      return result;
    } catch (requestError: unknown) {
      setError(toApiError(requestError));
      return null;
    } finally {
      setLoading(false);
    }
  }, [orderNo]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { data, loading, error, refresh };
}

export interface CancelOrderState {
  submitting: boolean;
  resultUnknown: boolean;
  error: ApiError | null;
  submit: () => Promise<OrderResponse | null>;
  recover: () => Promise<OrderQueryResponse | null>;
}

/**
 * 取消订单并在响应未知时按订单号恢复。
 *
 * RESULT_UNKNOWN 会持久化保护标记，刷新后仍只允许查询详情而不重发 POST。
 */
export function useCancelOrder(orderNo: string): CancelOrderState {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [resultUnknown, setResultUnknown] = useState(
    () => getWriteOperationSession('cancel', orderNo).resultUnknown,
  );

  const recover = useCallback(async (): Promise<OrderQueryResponse | null> => {
    setError(null);
    try {
      const order = await getOrder(orderNo);
      if (order.status === 'CANCELLED' || order.status !== 'PENDING_PAYMENT') {
        clearWriteOperationSession('cancel', orderNo);
        setResultUnknown(false);
      }
      return order;
    } catch (requestError: unknown) {
      setError(toApiError(requestError));
      return null;
    }
  }, [orderNo]);

  const submit = useCallback(async (): Promise<OrderResponse | null> => {
    if (resultUnknown || submitting) {
      return null;
    }
    setSubmitting(true);
    setError(null);
    const session = getWriteOperationSession('cancel', orderNo);
    try {
      const order = await cancelOrder(orderNo, session.idempotencyKey);
      clearWriteOperationSession('cancel', orderNo);
      return order;
    } catch (requestError: unknown) {
      if (isResultUnknownError(requestError)) {
        markWriteResultUnknown('cancel', orderNo);
        setResultUnknown(true);
        return null;
      }
      setError(toApiError(requestError));
      return null;
    } finally {
      setSubmitting(false);
    }
  }, [orderNo, resultUnknown, submitting]);

  return { submitting, resultUnknown, error, submit, recover };
}

export interface PaymentActionState {
  payment: PaymentResponse | null;
  submitting: boolean;
  querying: boolean;
  resultUnknown: boolean;
  error: ApiError | null;
  submit: () => Promise<PaymentResponse | null>;
  query: () => Promise<PaymentResponse | null>;
}

export interface PaymentQueryState {
  payment: PaymentResponse | null;
  querying: boolean;
  error: ApiError | null;
  query: () => Promise<PaymentResponse | null>;
}

/**
 * 查询订单支付记录，用于订单详情跳转电子票等只读场景。
 *
 * 页面不可直接调用 API，避免把读取、错误分类和后续恢复规则分散到多个容器。
 */
export function usePaymentQuery(orderNo: string): PaymentQueryState {
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [querying, setQuerying] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const query = useCallback(async (): Promise<PaymentResponse | null> => {
    setQuerying(true);
    setError(null);
    try {
      const result = await getPayment(orderNo);
      setPayment(result);
      return result;
    } catch (requestError: unknown) {
      setError(toApiError(requestError));
      return null;
    } finally {
      setQuerying(false);
    }
  }, [orderNo]);

  return { payment, querying, error, query };
}

/** Mock 支付只发送订单号与稳定幂等键，响应未知后永久切换为只读查询。 */
export function usePaymentAction(orderNo: string): PaymentActionState {
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [querying, setQuerying] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [resultUnknown, setResultUnknown] = useState(
    () => getWriteOperationSession('payment', orderNo).resultUnknown,
  );

  const query = useCallback(async (): Promise<PaymentResponse | null> => {
    setQuerying(true);
    setError(null);
    try {
      const result = await getPayment(orderNo);
      setPayment(result);
      if (result.paymentStatus === 'SUCCESS' || result.orderStatus !== 'PAYING') {
        clearWriteOperationSession('payment', orderNo);
        setResultUnknown(false);
      }
      return result;
    } catch (requestError: unknown) {
      setError(toApiError(requestError));
      return null;
    } finally {
      setQuerying(false);
    }
  }, [orderNo]);

  const submit = useCallback(async (): Promise<PaymentResponse | null> => {
    if (resultUnknown || submitting) {
      return null;
    }
    setSubmitting(true);
    setError(null);
    const session = getWriteOperationSession('payment', orderNo);
    try {
      const result = await payOrder(orderNo, session.idempotencyKey);
      setPayment(result);
      clearWriteOperationSession('payment', orderNo);
      return result;
    } catch (requestError: unknown) {
      if (isResultUnknownError(requestError)) {
        markWriteResultUnknown('payment', orderNo);
        setResultUnknown(true);
        return null;
      }
      setError(toApiError(requestError));
      return null;
    } finally {
      setSubmitting(false);
    }
  }, [orderNo, resultUnknown, submitting]);

  return { payment, submitting, querying, resultUnknown, error, submit, query };
}

const PAYMENT_POLL_INTERVAL_MS = 2_000;
const PAYMENT_MAX_POLLS = 15;

/**
 * 有界查询支付结果。
 *
 * 自动查询最多 15 次；页面卸载会停止定时器，任何终态都不会触发支付 POST。
 */
export function usePaymentResult(orderNo: string): PaymentActionState {
  const action = usePaymentAction(orderNo);
  const [pollCount, setPollCount] = useState(0);
  const shouldContinue =
    action.payment === null ||
    (action.payment.paymentStatus === 'PROCESSING' && action.payment.orderStatus === 'PAYING');

  useEffect(() => {
    if (!orderNo || !shouldContinue || pollCount >= PAYMENT_MAX_POLLS) {
      return undefined;
    }
    const timer = window.setTimeout(
      () => {
        void action.query().finally(() => setPollCount((count) => count + 1));
      },
      pollCount === 0 ? 0 : PAYMENT_POLL_INTERVAL_MS,
    );
    return () => window.clearTimeout(timer);
  }, [action.query, orderNo, pollCount, shouldContinue]);

  return action;
}

/** 查询当前用户电子票，只保存服务端只读快照。 */
export function useElectronicTicket(ticketId: string): QueryState<ElectronicTicketResponse> {
  const [data, setData] = useState<ElectronicTicketResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const refresh = useCallback(async (): Promise<ElectronicTicketResponse | null> => {
    if (!ticketId) {
      setLoading(false);
      return null;
    }
    setLoading(true);
    try {
      const result = await getElectronicTicket(ticketId);
      setData(result);
      setError(null);
      return result;
    } catch (requestError: unknown) {
      setError(toApiError(requestError));
      return null;
    } finally {
      setLoading(false);
    }
  }, [ticketId]);
  useEffect(() => void refresh(), [refresh]);
  return { data, loading, error, refresh };
}

export interface RefundPageState {
  impact: RefundImpactResponse | null;
  alternatives: AlternativeShowsResponse | null;
  refund: RefundResponse | null;
  loading: boolean;
  submitting: boolean;
  resultUnknown: boolean;
  error: ApiError | null;
  alternativeError: ApiError | null;
  submit: (refundReason?: string) => Promise<RefundResponse | null>;
  recover: () => Promise<RefundResponse | null>;
}

/** 查询退票影响并编排稳定幂等退票与只读恢复。 */
export function useRefundPage(orderNo: string): RefundPageState {
  const [impact, setImpact] = useState<RefundImpactResponse | null>(null);
  const [alternatives, setAlternatives] = useState<AlternativeShowsResponse | null>(null);
  const [refund, setRefund] = useState<RefundResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [alternativeError, setAlternativeError] = useState<ApiError | null>(null);
  const [resultUnknown, setResultUnknown] = useState(
    () => getWriteOperationSession('refund', orderNo).resultUnknown,
  );

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.allSettled([getRefundImpact(orderNo), getAlternativeShows(orderNo), getRefund(orderNo)])
      .then(([impactResult, alternativeResult, refundResult]) => {
        if (!active) {
          return;
        }
        if (impactResult.status === 'fulfilled') {
          setImpact(impactResult.value);
          setError(null);
        } else {
          setError(toApiError(impactResult.reason));
        }
        if (alternativeResult.status === 'fulfilled') {
          setAlternatives(alternativeResult.value);
          setAlternativeError(null);
        } else {
          setAlternativeError(toApiError(alternativeResult.reason));
        }
        if (refundResult.status === 'fulfilled') {
          setRefund(refundResult.value);
          // 查到任意退款记录都说明原写请求已被服务端受理，不能继续保留结果未知保护。
          clearWriteOperationSession('refund', orderNo);
          setResultUnknown(false);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [orderNo]);

  const recover = useCallback(async (): Promise<RefundResponse | null> => {
    setError(null);
    try {
      const result = await getRefund(orderNo);
      setRefund(result);
      // REQUESTED、PROCESSING 和 SUCCESS 都是服务端明确结果，只读查询后允许页面退出未知态。
      clearWriteOperationSession('refund', orderNo);
      setResultUnknown(false);
      return result;
    } catch (requestError: unknown) {
      setError(toApiError(requestError));
      return null;
    }
  }, [orderNo]);

  const submit = useCallback(
    async (refundReason?: string): Promise<RefundResponse | null> => {
      if (resultUnknown || submitting) {
        return null;
      }
      setSubmitting(true);
      setError(null);
      const session = getWriteOperationSession('refund', orderNo);
      try {
        const result = await createRefund(
          orderNo,
          { refundReason, clientRequestId: session.clientRequestId as string },
          session.idempotencyKey,
        );
        setRefund(result);
        clearWriteOperationSession('refund', orderNo);
        return result;
      } catch (requestError: unknown) {
        if (isResultUnknownError(requestError)) {
          markWriteResultUnknown('refund', orderNo);
          setResultUnknown(true);
          return null;
        }
        setError(toApiError(requestError));
        return null;
      } finally {
        setSubmitting(false);
      }
    },
    [orderNo, resultUnknown, submitting],
  );

  return {
    impact,
    alternatives,
    refund,
    loading,
    submitting,
    resultUnknown,
    error,
    alternativeError,
    submit,
    recover,
  };
}
