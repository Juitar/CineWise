import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import { getAvailableMovies } from './api';
import type { AvailableMovie } from './types';

export interface AvailableMoviesState {
  movies: AvailableMovie[];
  error: ApiError | null;
  isLoading: boolean;
  retry: () => void;
}

function toApiError(error: unknown): ApiError {
  return error instanceof ApiError
    ? error
    : new ApiError('可售影片查询失败', { kind: 'INVALID_RESPONSE' });
}

/** 排期区域独立查询和重试；影院详情成功时不会被本请求失败清空。 */
export function useAvailableMovies(cinemaId?: string): AvailableMoviesState {
  const [movies, setMovies] = useState<AvailableMovie[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const [isLoading, setIsLoading] = useState(Boolean(cinemaId));
  const [retryVersion, setRetryVersion] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const currentSequence = requestSequence.current + 1;
    requestSequence.current = currentSequence;
    if (!cinemaId) {
      setMovies([]);
      setError(null);
      setIsLoading(false);
      return undefined;
    }

    const controller = new AbortController();
    setMovies([]);
    setError(null);
    setIsLoading(true);
    void getAvailableMovies(cinemaId, controller.signal)
      .then((response) => {
        if (requestSequence.current === currentSequence) setMovies(response.movies);
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
    movies,
    error,
    isLoading,
    retry: useCallback(() => setRetryVersion((current) => current + 1), []),
  };
}
