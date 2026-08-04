import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import type { ContentPageResponse, MovieSummary } from '../../shared/types/api';
import { queryMovies } from './api';
import type { NormalizedMovieListQuery } from './movieListQuery';

/** 影片列表页面需要的完整查询状态；data 在刷新失败时保留为上一次成功快照。 */
export interface MovieListState {
  data: ContentPageResponse<MovieSummary> | null;
  error: ApiError | null;
  isLoading: boolean;
  isOffline: boolean;
  isOfflineSnapshot: boolean;
  isRefreshing: boolean;
  retry: () => void;
}

/** 保留公共 ApiError 的状态和 traceId；未知异常统一按无法识别的响应处理。 */
function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  return new ApiError('影片查询失败', { kind: 'INVALID_RESPONSE' });
}

/** 服务端渲染环境没有 navigator，按在线处理，等待浏览器接管真实网络状态。 */
function browserIsOnline(): boolean {
  return typeof navigator === 'undefined' ? true : navigator.onLine;
}

/**
 * 管理影片列表只读查询。
 *
 * URL 条件变化会取消旧请求，并用递增请求号阻止迟到响应覆盖新结果。已有数据刷新失败时继续保留
 * 当前内存快照，但不会写入 localStorage、IndexedDB 或 Service Worker。
 */
export function useMovieList(query: NormalizedMovieListQuery): MovieListState {
  const [data, setData] = useState<ContentPageResponse<MovieSummary> | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [isRequesting, setIsRequesting] = useState(true);
  const [isOffline, setIsOffline] = useState(!browserIsOnline());
  const [retryVersion, setRetryVersion] = useState(0);
  const requestSequence = useRef(0);

  // 页面每次渲染都会创建 query 对象；按字段稳定化后，只有实际查询条件变化才重新请求。
  const stableQuery = useMemo<NormalizedMovieListQuery>(
    () => ({
      genre: query.genre,
      keyword: query.keyword,
      page: query.page,
      size: query.size,
    }),
    [query.genre, query.keyword, query.page, query.size],
  );

  useEffect(() => {
    // online/offline 只描述浏览器网络状态，不代表后端健康；请求结果仍由 ApiError 决定。
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
    // AbortController 节省旧请求开销；递增序号是第二道保护，防止已到达的旧响应晚于新响应写回页面。
    const currentSequence = requestSequence.current + 1;
    requestSequence.current = currentSequence;
    // 刷新时不清空 data，避免页面闪白；失败后页面会明确标记“保留上次加载的影片”。
    setIsRequesting(true);
    setError(null);

    void queryMovies(stableQuery, controller.signal)
      .then((response) => {
        if (requestSequence.current === currentSequence) {
          setData(response);
        }
      })
      .catch((requestError: unknown) => {
        const apiError = toApiError(requestError);
        // 组件卸载或筛选变化触发的主动取消不是用户可处理的错误，不显示失败提示。
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

  const retry = useCallback(() => {
    // 只读查询允许用户主动重试；递增版本只复用当前条件，不修改 URL 或生成新业务参数。
    setRetryVersion((current) => current + 1);
  }, []);

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
