import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { ApiError } from '../../shared/api/ApiError';
import type {
  AdminOrderDetailSnapshot,
  AdminOrderListQuery,
  AdminOrderPageResponse,
} from './types';
import { queryAdminOrderDetail, queryAdminOrders } from './api';
import { toAdminApiError } from './errors';
import { mapAdminOrderDetail, mapAdminOrderPage } from './mappers';

export interface AdminOrdersQueryState {
  data: AdminOrderPageResponse | null;
  error: ApiError | null;
  isLoading: boolean;
  isRefreshing: boolean;
  retry: () => void;
}

/**
 * 管理订单只读分页查询。
 *
 * 筛选变化会取消旧请求并拒绝迟到响应；刷新失败时保留上一份内存快照，供 301002/5xx
 * 显式降级展示，但不会缓存用户关键字、完整邮箱或订单数据到持久化存储。
 */
export function useAdminOrders(query: AdminOrderListQuery): AdminOrdersQueryState {
  const [data, setData] = useState<AdminOrderPageResponse | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [isRequesting, setIsRequesting] = useState(true);
  const [retryVersion, setRetryVersion] = useState(0);
  const requestSequence = useRef(0);
  const stableQuery = useMemo<AdminOrderListQuery>(
    () => ({
      dateFrom: query.dateFrom,
      dateTo: query.dateTo,
      movieId: query.movieId,
      orderNo: query.orderNo,
      page: query.page,
      showId: query.showId,
      size: query.size,
      status: query.status,
      userKeyword: query.userKeyword,
    }),
    [
      query.dateFrom,
      query.dateTo,
      query.movieId,
      query.orderNo,
      query.page,
      query.showId,
      query.size,
      query.status,
      query.userKeyword,
    ],
  );

  useEffect(() => {
    const controller = new AbortController();
    const currentSequence = requestSequence.current + 1;
    requestSequence.current = currentSequence;
    setIsRequesting(true);
    setError(null);

    void queryAdminOrders(stableQuery, controller.signal)
      .then((response) => {
        if (requestSequence.current === currentSequence) {
          setData(mapAdminOrderPage(response));
        }
      })
      .catch((requestError: unknown) => {
        const apiError = toAdminApiError(requestError);
        if (apiError.kind !== 'CANCELLED' && requestSequence.current === currentSequence) {
          setError(apiError);
        }
      })
      .finally(() => {
        if (requestSequence.current === currentSequence) {
          setIsRequesting(false);
        }
      });

    return () => controller.abort();
  }, [retryVersion, stableQuery]);

  const retry = useCallback(() => setRetryVersion((current) => current + 1), []);
  return {
    data,
    error,
    isLoading: isRequesting && data === null,
    isRefreshing: isRequesting && data !== null,
    retry,
  };
}

export interface AdminOrderDetailQueryState {
  data: AdminOrderDetailSnapshot | null;
  error: ApiError | null;
  isLoading: boolean;
  retry: () => void;
}

/** 只在抽屉选择了订单号时查询详情；切换订单或关闭抽屉会取消旧请求。 */
export function useAdminOrderDetail(orderNo: string | null): AdminOrderDetailQueryState {
  const [data, setData] = useState<AdminOrderDetailSnapshot | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [retryVersion, setRetryVersion] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const currentSequence = requestSequence.current + 1;
    requestSequence.current = currentSequence;
    if (!orderNo) {
      setData(null);
      setError(null);
      setIsLoading(false);
      return undefined;
    }

    const controller = new AbortController();
    setData(null);
    setError(null);
    setIsLoading(true);
    void queryAdminOrderDetail(orderNo, controller.signal)
      .then((response) => {
        if (requestSequence.current === currentSequence) {
          setData(mapAdminOrderDetail(response));
        }
      })
      .catch((requestError: unknown) => {
        const apiError = toAdminApiError(requestError);
        if (apiError.kind !== 'CANCELLED' && requestSequence.current === currentSequence) {
          setError(apiError);
        }
      })
      .finally(() => {
        if (requestSequence.current === currentSequence) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [orderNo, retryVersion]);

  const retry = useCallback(() => setRetryVersion((current) => current + 1), []);
  return { data, error, isLoading, retry };
}
