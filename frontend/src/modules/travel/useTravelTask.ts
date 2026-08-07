import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../shared/api/ApiError';
import {
  getTravelAdvice,
  getTravelTask,
  getTravelTaskByOrder,
  refreshTravelAdvice,
  updateTravelReminder,
} from './api';
import { TravelContractError } from './contract';
import type { TravelAdvice, TravelTask, UpdateReminderRequest } from './types';

function safeError(error: unknown, fallback: string): ApiError {
  if (error instanceof ApiError) return error;
  if (error instanceof TravelContractError) {
    return new ApiError(error.message, { kind: 'INVALID_RESPONSE' });
  }
  return new ApiError(fallback, { kind: 'INVALID_RESPONSE' });
}

function actionMessage(error: ApiError): string {
  if (error.isResultUnknown) return '操作结果暂时无法确认，请重新查询任务，不要重复提交';
  if (error.status === 429 || error.code === 107001) return '建议刷新过于频繁，请稍后再试';
  if (error.status === 409 || error.code === 207002 || error.code === 207003)
    return '任务状态已经变化，请重新查询最新结果';
  if (error.status === 503 || error.code === 207004) return '出行服务暂时不可用，已有内容仍可查看';
  return error.message;
}

export function useTravelTask(taskId: string) {
  const [task, setTask] = useState<TravelTask | null>(null);
  const [advice, setAdvice] = useState<TravelAdvice | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isUpdatingReminder, setIsUpdatingReminder] = useState(false);
  const [isReminderResultUnknown, setIsReminderResultUnknown] = useState(false);
  const requestRef = useRef<AbortController | null>(null);
  const refreshingRef = useRef(false);
  const updatingRef = useRef(false);

  const reload = useCallback(async () => {
    requestRef.current?.abort();
    const controller = new AbortController();
    requestRef.current = controller;
    setIsLoading(true);
    setError(null);
    try {
      const [nextTask, nextAdvice] = await Promise.all([
        getTravelTask(taskId, controller.signal),
        getTravelAdvice(taskId, controller.signal),
      ]);
      if (controller.signal.aborted) return false;
      setTask(nextTask);
      setAdvice(nextAdvice);
      setNotice(null);
      setIsReminderResultUnknown(false);
      return true;
    } catch (requestError) {
      const nextError = safeError(requestError, '出行任务加载失败');
      if (nextError.kind !== 'CANCELLED' && !controller.signal.aborted) setError(nextError);
      return false;
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null;
        setIsLoading(false);
      }
    }
  }, [taskId]);

  useEffect(() => {
    void reload();
    return () => requestRef.current?.abort();
  }, [reload]);

  const refresh = useCallback(async () => {
    if (refreshingRef.current) return false;
    refreshingRef.current = true;
    setIsRefreshing(true);
    setNotice(null);
    try {
      setAdvice(await refreshTravelAdvice(taskId));
      return true;
    } catch (requestError) {
      setNotice(actionMessage(safeError(requestError, '建议刷新失败')));
      return false;
    } finally {
      refreshingRef.current = false;
      setIsRefreshing(false);
    }
  }, [taskId]);

  const updateReminder = useCallback(
    async (request: UpdateReminderRequest) => {
      if (updatingRef.current || isReminderResultUnknown) return false;
      updatingRef.current = true;
      setIsUpdatingReminder(true);
      setNotice(null);
      try {
        const updated = await updateTravelReminder(taskId, request);
        setTask((current) => (current ? { ...current, ...updated } : current));
        setNotice('提醒时间已更新');
        return true;
      } catch (requestError) {
        const nextError = safeError(requestError, '提醒时间更新失败');
        if (nextError.isResultUnknown) setIsReminderResultUnknown(true);
        setNotice(actionMessage(nextError));
        return false;
      } finally {
        updatingRef.current = false;
        setIsUpdatingReminder(false);
      }
    },
    [isReminderResultUnknown, taskId],
  );

  return {
    task,
    advice,
    error,
    notice,
    isLoading,
    isRefreshing,
    isUpdatingReminder,
    isReminderResultUnknown,
    reload,
    refresh,
    updateReminder,
  };
}

export function useTravelTaskByOrder() {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const loadingRef = useRef(false);

  const find = useCallback(async (orderId: string): Promise<TravelTask | null> => {
    if (loadingRef.current) return null;
    loadingRef.current = true;
    setIsLoading(true);
    setError(null);
    try {
      return await getTravelTaskByOrder(orderId);
    } catch (requestError) {
      const nextError = safeError(requestError, '出行任务查询失败');
      setError(nextError);
      throw nextError;
    } finally {
      loadingRef.current = false;
      setIsLoading(false);
    }
  }, []);

  return { error, find, isLoading };
}
