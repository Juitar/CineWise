import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import type { MovieDetail } from '../../shared/types/api';
import { getMovieDetail } from './api';

export interface MovieDetailState {
  data: MovieDetail | null;
  error: ApiError | null;
  isLoading: boolean;
  retry: () => void;
}

function toApiError(error: unknown): ApiError {
  return error instanceof ApiError
    ? error
    : new ApiError('影片详情查询失败', { kind: 'INVALID_RESPONSE' });
}

/** 影片 ID 变化时取消旧请求，避免旧影片资料覆盖当前详情页。 */
export function useMovieDetail(movieId?: string): MovieDetailState {
  const [data, setData] = useState<MovieDetail | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [isLoading, setIsLoading] = useState(Boolean(movieId));
  const [retryVersion, setRetryVersion] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const currentSequence = requestSequence.current + 1;
    requestSequence.current = currentSequence;
    if (!movieId) {
      setData(null);
      setError(null);
      setIsLoading(false);
      return undefined;
    }

    const controller = new AbortController();
    setData(null);
    setError(null);
    setIsLoading(true);
    void getMovieDetail(movieId, controller.signal)
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
  }, [movieId, retryVersion]);

  return {
    data,
    error,
    isLoading,
    retry: useCallback(() => setRetryVersion((current) => current + 1), []),
  };
}
