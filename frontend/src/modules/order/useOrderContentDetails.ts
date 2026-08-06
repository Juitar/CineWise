import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getCinemaDetail, getMovieDetail } from '../content/api';
import type { CinemaDetail, MovieDetail } from '../../shared/types/api';

export interface OrderContentReference {
  movieId?: string;
  cinemaId?: string;
}

export interface OrderContentDetailsState {
  moviesById: ReadonlyMap<string, MovieDetail>;
  cinemasById: ReadonlyMap<string, CinemaDetail>;
  isLoading: boolean;
  hasUnavailableContent: boolean;
  refresh: () => void;
}

interface DetailLookup<T> {
  id: string;
  detail: T | null;
}

function toDistinctBusinessIds(values: Array<string | undefined>): string[] {
  return [...new Set(values.filter((value): value is string => /^[1-9]\d*$/.test(value ?? '')))];
}

async function loadDetails<T>(
  ids: string[],
  queryDetail: (id: string, signal: AbortSignal) => Promise<T>,
  signal: AbortSignal,
): Promise<DetailLookup<T>[]> {
  return Promise.all(
    ids.map(async (id) => {
      try {
        return { id, detail: await queryDetail(id, signal) };
      } catch {
        // 内容资料是订单展示的补充。单项失败不能替换订单的权威查询结果。
        return { id, detail: null };
      }
    }),
  );
}

/**
 * 为当前订单集合补充 D 的只读影片、影院资料。
 *
 * 订单中的业务 ID 是唯一查询依据；每次刷新创建新的取消信号，避免旧分页或旧票据的内容响应
 * 覆盖当前页面。内容失败只记录为展示降级，绝不影响订单状态或触发任何交易写操作。
 */
export function useOrderContentDetails(
  references: OrderContentReference[],
): OrderContentDetailsState {
  const referenceKey = useMemo(() => {
    const movieIds = toDistinctBusinessIds(references.map((reference) => reference.movieId));
    const cinemaIds = toDistinctBusinessIds(references.map((reference) => reference.cinemaId));

    return `${movieIds.join(',')}|${cinemaIds.join(',')}`;
  }, [references]);
  const requestSequence = useRef(0);
  const [refreshSequence, setRefreshSequence] = useState(0);
  const [state, setState] = useState<Omit<OrderContentDetailsState, 'refresh'>>({
    moviesById: new Map(),
    cinemasById: new Map(),
    isLoading: false,
    hasUnavailableContent: false,
  });

  const refresh = useCallback(() => {
    setRefreshSequence((currentSequence) => currentSequence + 1);
  }, []);

  useEffect(() => {
    const [movieIdPart = '', cinemaIdPart = ''] = referenceKey.split('|');
    const movieIds = movieIdPart === '' ? [] : movieIdPart.split(',');
    const cinemaIds = cinemaIdPart === '' ? [] : cinemaIdPart.split(',');
    const requestId = requestSequence.current + 1;
    requestSequence.current = requestId;
    const controller = new AbortController();

    if (movieIds.length === 0 && cinemaIds.length === 0) {
      setState({
        moviesById: new Map(),
        cinemasById: new Map(),
        isLoading: false,
        hasUnavailableContent: false,
      });
      return () => controller.abort();
    }

    setState((previousState) => ({
      ...previousState,
      isLoading: true,
      hasUnavailableContent: false,
    }));

    void Promise.all([
      loadDetails(movieIds, getMovieDetail, controller.signal),
      loadDetails(cinemaIds, getCinemaDetail, controller.signal),
    ]).then(([movieResults, cinemaResults]) => {
      if (requestSequence.current !== requestId || controller.signal.aborted) {
        return;
      }

      const moviesById = new Map(
        movieResults.flatMap(({ id, detail }) => (detail ? [[id, detail] as const] : [])),
      );
      const cinemasById = new Map(
        cinemaResults.flatMap(({ id, detail }) => (detail ? [[id, detail] as const] : [])),
      );
      const hasUnavailableContent =
        moviesById.size !== movieIds.length || cinemasById.size !== cinemaIds.length;

      setState({
        moviesById,
        cinemasById,
        isLoading: false,
        hasUnavailableContent,
      });
    });

    return () => controller.abort();
  }, [referenceKey, refreshSequence]);

  return { ...state, refresh };
}
