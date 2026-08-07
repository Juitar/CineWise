import { apiRequest } from '../../shared/api/client';
import { parseTravelAdvice, parseTravelTask, parseTravelTaskUpdate } from './contract';
import type { TravelAdvice, TravelTask, TravelTaskUpdate, UpdateReminderRequest } from './types';

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
