import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import type { CinemaSummary, ContentPageResponse } from '../../shared/types/api';
import { queryCinemas } from './api';
import type { NormalizedCinemaListQuery } from './cinemaListQuery';

/** 影院列表完整查询状态；刷新失败时 data 保留上一次成功快照。 */
export interface CinemaListState {
  data: ContentPageResponse<CinemaSummary> | null;
  error: ApiError | null;
  isLoading: boolean;
  isOffline: boolean;
  isOfflineSnapshot: boolean;
  isRefreshing: boolean;
  retry: () => void;
}

function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  return new ApiError('影院查询失败', { kind: 'INVALID_RESPONSE' });
}

function browserIsOnline(): boolean {
  return typeof navigator === 'undefined' ? true : navigator.onLine;
}

/**
 * 管理影院列表只读查询。
 *
 * 条件变化时取消旧请求并用请求序号阻止迟到响应覆盖当前城市和关键词；离线快照只存在页面内存。
 */
export function useCinemaList(query: NormalizedCinemaListQuery): CinemaListState {
  const [data, setData] = useState<ContentPageResponse<CinemaSummary> | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [isRequesting, setIsRequesting] = useState(true);
  const [isOffline, setIsOffline] = useState(!browserIsOnline());
  const [retryVersion, setRetryVersion] = useState(0);
  const requestSequence = useRef(0);
  const stableQuery = useMemo<NormalizedCinemaListQuery>(
    () => ({
      keyword: query.keyword,
      location: query.location,
      page: query.page,
      size: query.size,
    }),
    [query.keyword, query.location, query.page, query.size],
  );

  useEffect(() => {
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const currentSequence = requestSequence.current + 1;
    requestSequence.current = currentSequence;
    setIsRequesting(true);
    setError(null);

    void queryCinemas(stableQuery, controller.signal)
      .then((response) => {
        if (requestSequence.current === currentSequence) {
          setData(response);
        }
      })
      .catch((requestError: unknown) => {
        const apiError = toApiError(requestError);
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
    isOffline,
    isOfflineSnapshot: isOffline && data !== null,
    isRefreshing: isRequesting && data !== null,
    retry,
  };
}
