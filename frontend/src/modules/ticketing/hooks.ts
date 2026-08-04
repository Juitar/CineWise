import { useState, useEffect, useCallback, useRef } from 'react';
import { getShows, getSeatMap } from './api';
import type { ShowSummary, SeatMapResponse } from './types';
import { ApiError } from '../../shared/api/ApiError';

export interface UseShowsResult {
  loading: boolean;
  shows: ShowSummary[];
  error: ApiError | null;
  isEmpty: boolean;
  refetch: () => Promise<void>;
}

export function useShows(movieId?: string, cinemaId?: string): UseShowsResult {
  const [loading, setLoading] = useState<boolean>(false);
  const [shows, setShows] = useState<ShowSummary[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const requestSequenceRef = useRef(0);

  const fetchData = useCallback(async () => {
    const requestSequence = ++requestSequenceRef.current;
    if (!movieId || !cinemaId) {
      setShows([]);
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await getShows(movieId, cinemaId);
      if (requestSequence === requestSequenceRef.current) {
        setShows(Array.isArray(result) ? result : []);
      }
    } catch (err: unknown) {
      if (requestSequence === requestSequenceRef.current) {
        setError(
          err instanceof ApiError
            ? err
            : new ApiError(String(err), { kind: 'HTTP', status: 500, code: -1 }),
        );
        setShows([]);
      }
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setLoading(false);
      }
    }
  }, [movieId, cinemaId]);

  useEffect(() => {
    void fetchData();
    return () => {
      // 参数切换或组件卸载后，旧场次响应不得覆盖当前筛选条件。
      requestSequenceRef.current += 1;
    };
  }, [fetchData]);

  return {
    loading,
    shows,
    error,
    isEmpty: !loading && !error && shows.length === 0,
    refetch: fetchData,
  };
}

export interface UseSeatMapResult {
  loading: boolean;
  seatMap: SeatMapResponse | null;
  error: ApiError | null;
  refetch: () => Promise<void>;
}

export function useSeatMap(showId?: string): UseSeatMapResult {
  const [loading, setLoading] = useState<boolean>(false);
  const [seatMap, setSeatMap] = useState<SeatMapResponse | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const requestSequenceRef = useRef(0);

  const fetchData = useCallback(async () => {
    const requestSequence = ++requestSequenceRef.current;
    if (!showId) {
      setSeatMap(null);
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await getSeatMap(showId);
      if (requestSequence === requestSequenceRef.current) {
        setSeatMap(result);
      }
    } catch (err: unknown) {
      if (requestSequence === requestSequenceRef.current) {
        setError(
          err instanceof ApiError
            ? err
            : new ApiError(String(err), { kind: 'HTTP', status: 500, code: -1 }),
        );
        setSeatMap(null);
      }
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setLoading(false);
      }
    }
  }, [showId]);

  useEffect(() => {
    void fetchData();
    return () => {
      // 场次切换或组件卸载后，旧座位图不得成为当前建单校验依据。
      requestSequenceRef.current += 1;
    };
  }, [fetchData]);

  return {
    loading,
    seatMap,
    error,
    refetch: fetchData,
  };
}
