import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../../shared/api/ApiError';
import { queryContentSources, queryContentSync, requestContentSync } from './api';
import type { ContentSourceStatus, ContentSyncTask } from './types';

const key = 'cinewise:admin-content-sync';
const isTerminalTask = (task: ContentSyncTask) =>
  task.status === 'SUCCESS' || task.status === 'PARTIAL' || task.status === 'FAILED';
const toError = (error: unknown) =>
  error instanceof ApiError ? error : new ApiError('请求失败', { kind: 'NETWORK' });

export function useAdminContentSync() {
  const [sources, setSources] = useState<ContentSourceStatus[] | null>(null);
  const [task, setTask] = useState<ContentSyncTask | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [resultUnknown, setResultUnknown] = useState(() => sessionStorage.getItem(key) !== null);
  const isTaskInProgress = task?.status === 'PENDING' || task?.status === 'RUNNING';
  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSources(await queryContentSources());
    } catch (e) {
      setError(toError(e));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  const submit = useCallback(
    async (cityName: string): Promise<ContentSyncTask | null> => {
      if (submitting || resultUnknown || isTaskInProgress) return null;
      const clientRequestId = crypto.randomUUID();
      sessionStorage.setItem(key, clientRequestId);
      setSubmitting(true);
      setError(null);
      try {
        const next = await requestContentSync(clientRequestId, cityName);
        setTask(next);
        if (isTerminalTask(next)) sessionStorage.removeItem(key);
        setResultUnknown(false);
        await refresh();
        return next;
      } catch (e) {
        const apiError = toError(e);
        if (apiError.isResultUnknown) setResultUnknown(true);
        else {
          sessionStorage.removeItem(key);
          setError(apiError);
        }
        return null;
      } finally {
        setSubmitting(false);
      }
    },
    [isTaskInProgress, resultUnknown, submitting, refresh],
  );
  const recover = useCallback(async () => {
    const id = sessionStorage.getItem(key);
    if (!id) return;
    setSubmitting(true);
    setError(null);
    try {
      const next = await queryContentSync(id);
      setTask(next);
      if (isTerminalTask(next)) sessionStorage.removeItem(key);
      setResultUnknown(false);
      await refresh();
    } catch (e) {
      setError(toError(e));
    } finally {
      setSubmitting(false);
    }
  }, [refresh]);
  useEffect(() => {
    if (resultUnknown && task === null) void recover();
  }, [recover, resultUnknown, task]);
  return {
    sources,
    task,
    error,
    loading,
    submitting,
    resultUnknown,
    isTaskInProgress,
    refresh,
    submit,
    recover,
  };
}
