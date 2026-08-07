import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import { getAvailableCinemas } from './api';
import type { AvailableCinemasResponse } from './types';

export interface AvailableCinemasState {
  data: AvailableCinemasResponse | null;
  error: ApiError | null;
  isLoading: boolean;
  isOfflineSnapshot: boolean;
  isRefreshing: boolean;
  retry: () => void;
}

function browserIsOnline(): boolean {
  return typeof navigator === 'undefined' ? true : navigator.onLine;
}

function toApiError(error: unknown): ApiError {
  return error instanceof ApiError
    ? error
    : new ApiError('可售影院查询失败', { kind: 'INVALID_RESPONSE' });
}

/** 只读查询的取消、竞态、重试及当前页面内存快照管理。 */
export function useAvailableCinemas(movieId?: string): AvailableCinemasState {
  const [data, setData] = useState<AvailableCinemasResponse | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [isRequesting, setIsRequesting] = useState(Boolean(movieId));
  const [isOffline, setIsOffline] = useState(!browserIsOnline());
  const [retryVersion, setRetryVersion] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const goOnline = () => setIsOffline(false);
    const goOffline = () => setIsOffline(true);
    window.addEventListener('online', goOnline);
    window.addEventListener('offline', goOffline);
    return () => {
      window.removeEventListener('online', goOnline);
      window.removeEventListener('offline', goOffline);
    };
  }, []);

  useEffect(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;
    if (!movieId) {
      setData(null);
      setError(null);
      setIsRequesting(false);
      return undefined;
    }

    const controller = new AbortController();
    setIsRequesting(true);
    setError(null);
    void getAvailableCinemas(movieId, controller.signal)
      .then((response) => {
        if (requestSequence.current === sequence) setData(response);
      })
      .catch((requestError: unknown) => {
        const apiError = toApiError(requestError);
        if (apiError.kind !== 'CANCELLED' && requestSequence.current === sequence) {
          setError(apiError);
        }
      })
      .finally(() => {
        if (requestSequence.current === sequence) setIsRequesting(false);
      });
    return () => controller.abort();
  }, [movieId, retryVersion]);

  return {
    data,
    error,
    isLoading: isRequesting && data === null,
    isOfflineSnapshot: isOffline && data !== null,
    isRefreshing: isRequesting && data !== null,
    retry: useCallback(() => setRetryVersion((current) => current + 1), []),
  };
}
