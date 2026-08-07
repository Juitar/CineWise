import { apiRequest } from '../../shared/api/client';

export interface ProfilePreference {
  enabled: boolean;
  version: number;
  updatedAt: string;
}

export interface ProfileTag {
  id: string;
  type: string;
  value: string;
  polarity: string;
  weight: number;
  confidence: number;
  source: string;
  status: string;
  expiresAt: string | null;
  version: number;
  updatedAt: string;
}

export interface ProfilePage {
  preference: ProfilePreference;
  tags: ProfileTag[];
  total: number;
}

/** 页面只通过本模块读取画像标签和个性化开关。 */
export function getMyProfile(signal?: AbortSignal): Promise<ProfilePage> {
  return apiRequest<ProfilePage>('/api/v1/profile/me/tags', {
    query: { page: 1, size: 100 },
    signal,
  });
}

/** 写操作使用当前画像版本和单次幂等键，网络失败时不自动重发。 */
export function updateMyPersonalization(
  enabled: boolean,
  version: number,
  idempotencyKey: string,
): Promise<ProfilePreference> {
  return apiRequest<ProfilePreference>('/api/v1/profile/me/personalization', {
    body: { enabled },
    headers: {
      'Idempotency-Key': idempotencyKey,
      'If-Match': String(version),
    },
    method: 'PUT',
  });
}
