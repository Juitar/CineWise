import { apiRequest } from '../../shared/api/client';
import type { ContentSourceStatus, ContentSyncTask } from './types';

export const queryContentSources = (signal?: AbortSignal): Promise<ContentSourceStatus[]> =>
  apiRequest<ContentSourceStatus[]>('/api/v1/admin/content/sources', { signal });

export const requestContentSync = (
  clientRequestId: string,
  cityName: string,
): Promise<ContentSyncTask> =>
  apiRequest<ContentSyncTask>('/api/v1/admin/content/sync', {
    method: 'POST',
    body: { clientRequestId, cityName },
  });

export const queryContentSync = (clientRequestId: string): Promise<ContentSyncTask> =>
  apiRequest<ContentSyncTask>(
    `/api/v1/admin/content/sync/by-request/${encodeURIComponent(clientRequestId)}`,
  );
