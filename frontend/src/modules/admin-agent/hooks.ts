import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { ApiError } from '../../shared/api/ApiError';
import { queryAdminAgentRunDetail, queryAdminAgentRuns } from './api';
import { toAdminAgentApiError } from './errors';
import { mapAdminAgentRunDetail, mapAdminAgentRunPage } from './mappers';
import type { AdminAgentRunDetail, AdminAgentRunListQuery, AdminAgentRunPage } from './types';

export interface AdminAgentRunsQueryState {
  data: AdminAgentRunPage | null;
  error: ApiError | null;
  isLoading: boolean;
  isRefreshing: boolean;
  retry: () => void;
}

/**
 * 管理员 Agent 运行列表查询。
 *
 * 条件变化会取消旧请求并丢弃迟到响应；列表只保存当前页面内存快照。
 */
export function useAdminAgentRuns(query: AdminAgentRunListQuery): AdminAgentRunsQueryState {
  const [data, setData] = useState<AdminAgentRunPage | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [isRequesting, setIsRequesting] = useState(true);
  const [retryVersion, setRetryVersion] = useState(0);
  const requestSequence = useRef(0);
  const stableQuery = useMemo<AdminAgentRunListQuery>(
    () => ({ ...query }),
    [query.page, query.size, query.startedFrom, query.startedTo, query.status, query.userKeyword],
  );

  useEffect(() => {
    const controller = new AbortController();
    const currentSequence = requestSequence.current + 1;
    requestSequence.current = currentSequence;
    setIsRequesting(true);
    setError(null);

    void queryAdminAgentRuns(stableQuery, controller.signal)
      .then((response) => {
        if (requestSequence.current === currentSequence) {
          setData(mapAdminAgentRunPage(response));
        }
      })
      .catch((requestError: unknown) => {
        const apiError = toAdminAgentApiError(requestError);
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

export interface AdminAgentRunDetailQueryState {
  data: AdminAgentRunDetail | null;
  error: ApiError | null;
  isLoading: boolean;
  retry: () => void;
}

/** 只在选中 runId 后查询详情；切换运行记录会取消旧请求。 */
export function useAdminAgentRunDetail(runId: string | null): AdminAgentRunDetailQueryState {
  const [data, setData] = useState<AdminAgentRunDetail | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [retryVersion, setRetryVersion] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const currentSequence = requestSequence.current + 1;
    requestSequence.current = currentSequence;
    if (!runId) {
      setData(null);
      setError(null);
      setIsLoading(false);
      return undefined;
    }

    const controller = new AbortController();
    setData(null);
    setError(null);
    setIsLoading(true);
    void queryAdminAgentRunDetail(runId, controller.signal)
      .then((response) => {
        if (requestSequence.current === currentSequence) {
          setData(mapAdminAgentRunDetail(response));
        }
      })
      .catch((requestError: unknown) => {
        const apiError = toAdminAgentApiError(requestError);
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
  }, [retryVersion, runId]);

  const retry = useCallback(() => setRetryVersion((current) => current + 1), []);
  return { data, error, isLoading, retry };
}
