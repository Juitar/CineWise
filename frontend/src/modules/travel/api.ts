import { apiRequest } from '../../shared/api/client';
import {
  parseTravelAdvice,
  parseTravelRoute,
  parseTravelTask,
  parseTravelTaskUpdate,
} from './contract';
import type {
  PlanTravelRouteRequest,
  TravelAdvice,
  TravelRoute,
  TravelTask,
  TravelTaskUpdate,
  UpdateReminderRequest,
} from './types';

export async function getTravelTask(taskId: string, signal?: AbortSignal): Promise<TravelTask> {
  return parseTravelTask(
    await apiRequest<unknown>(`/api/v1/travel/tasks/${encodeURIComponent(taskId)}`, { signal }),
  );
}

export async function getTravelTaskByOrder(
  orderId: string,
  signal?: AbortSignal,
): Promise<TravelTask> {
  return parseTravelTask(
    await apiRequest<unknown>(`/api/v1/travel/tasks/by-order/${encodeURIComponent(orderId)}`, {
      signal,
    }),
  );
}

export async function getTravelAdvice(taskId: string, signal?: AbortSignal): Promise<TravelAdvice> {
  return parseTravelAdvice(
    await apiRequest<unknown>(`/api/v1/travel/tasks/${encodeURIComponent(taskId)}/advice`, {
      signal,
    }),
  );
}

export async function refreshTravelAdvice(taskId: string): Promise<TravelAdvice> {
  return parseTravelAdvice(
    await apiRequest<unknown>(`/api/v1/travel/tasks/${encodeURIComponent(taskId)}/advice/refresh`, {
      method: 'POST',
    }),
  );
}

export async function updateTravelReminder(
  taskId: string,
  request: UpdateReminderRequest,
): Promise<TravelTaskUpdate> {
  return parseTravelTaskUpdate(
    await apiRequest<unknown>(`/api/v1/travel/tasks/${encodeURIComponent(taskId)}/reminder`, {
      method: 'PUT',
      headers: { 'If-Match': `"${request.version}"` },
      body: request,
    }),
  );
}

/**
 * 提交本次浏览器定位并返回不含坐标的路线摘要。
 * 调用方不得缓存 request，超时或断网后也不得自动重发本 POST。
 */
export async function planTravelRoute(
  taskId: string,
  request: PlanTravelRouteRequest,
  signal?: AbortSignal,
): Promise<TravelRoute> {
  return parseTravelRoute(
    await apiRequest<unknown>(`/api/v1/travel/tasks/${encodeURIComponent(taskId)}/route`, {
      body: request,
      method: 'POST',
      signal,
    }),
  );
}
