import { useState, useEffect, useCallback } from 'react';
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

  const fetchData = useCallback(async () => {
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
      setShows(Array.isArray(result) ? result : []);
    } catch (err: unknown) {
      setError(
        err instanceof ApiError
          ? err
          : new ApiError(String(err), { kind: 'HTTP', status: 500, code: -1 }),
      );
      setShows([]);
    } finally {
      setLoading(false);
    }
  }, [movieId, cinemaId]);

  useEffect(() => {
    fetchData();
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

  const fetchData = useCallback(async () => {
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
      setSeatMap(result);
    } catch (err: unknown) {
      setError(
        err instanceof ApiError
          ? err
          : new ApiError(String(err), { kind: 'HTTP', status: 500, code: -1 }),
      );
      setSeatMap(null);
    } finally {
      setLoading(false);
    }
  }, [showId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return {
    loading,
    seatMap,
    error,
    refetch: fetchData,
  };
}
