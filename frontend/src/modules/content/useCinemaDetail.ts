import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import type { CinemaDetail } from '../../shared/types/api';
import { queryCinemaDetail } from './api';

export interface CinemaDetailState {
  data: CinemaDetail | null;
  error: ApiError | null;
  isLoading: boolean;
  retry: () => void;
}

function toApiError(error: unknown): ApiError {
  return error instanceof ApiError
    ? error
    : new ApiError('影院详情查询失败', { kind: 'INVALID_RESPONSE' });
}

/** 影院 ID 变化时取消旧请求，并阻止迟到响应覆盖当前路由。 */
export function useCinemaDetail(cinemaId?: string): CinemaDetailState {
  const [data, setData] = useState<CinemaDetail | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [isLoading, setIsLoading] = useState(Boolean(cinemaId));
  const [retryVersion, setRetryVersion] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const currentSequence = requestSequence.current + 1;
    requestSequence.current = currentSequence;
    if (!cinemaId) {
      setData(null);
      setError(null);
      setIsLoading(false);
      return undefined;
    }

    const controller = new AbortController();
    setData(null);
    setError(null);
    setIsLoading(true);
    void queryCinemaDetail(cinemaId, controller.signal)
      .then((response) => {
        if (requestSequence.current === currentSequence) setData(response);
      })
      .catch((requestError: unknown) => {
        const apiError = toApiError(requestError);
        if (apiError.kind !== 'CANCELLED' && requestSequence.current === currentSequence) {
          setError(apiError);
        }
      })
      .finally(() => {
        if (requestSequence.current === currentSequence) setIsLoading(false);
      });
    return () => controller.abort();
  }, [cinemaId, retryVersion]);

  return {
    data,
    error,
    isLoading,
    retry: useCallback(() => setRetryVersion((current) => current + 1), []),
  };
}
