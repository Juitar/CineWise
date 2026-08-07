import { apiRequest } from '../../shared/api/client';

export interface ProfilePreference {
  enabled: boolean;
  version: number;
  updatedAt: string;
}

export type ProfileTagType = 'MOVIE_GENRE' | 'TIME' | 'CINEMA' | 'HALL' | 'PRICE' | 'SEAT';

export type ProfileTagPolarity = 'LIKE' | 'DISLIKE';

export type ProfileTagStatus = 'ACTIVE' | 'DISABLED' | 'EXPIRED' | 'DELETED';

export interface ProfileTag {
  id: string;
  type: ProfileTagType;
  value: string;
  polarity: ProfileTagPolarity;
  weight: number;
  confidence: number;
  source: 'MANUAL' | 'CONVERSATION' | 'BEHAVIOR';
  status: ProfileTagStatus;
  expiresAt: string | null;
  version: number;
  updatedAt: string;
}

export interface ProfilePage {
  preference: ProfilePreference;
  tags: ProfileTag[];
  total: number;
}

export interface CreateProfileTagRequest {
  type: ProfileTagType;
  value: string;
  polarity: ProfileTagPolarity;
  weight: number;
}

export interface UpdateProfileTagRequest {
  polarity?: ProfileTagPolarity;
  weight?: number;
  status?: Exclude<ProfileTagStatus, 'DELETED' | 'EXPIRED'>;
}

/** 页面只通过本模块读取画像标签和个性化开关。 */
export function getMyProfile(signal?: AbortSignal): Promise<ProfilePage> {
  return apiRequest<ProfilePage>('/api/v1/profile/me/tags', {
    cache: 'no-store',
    query: { page: 1, size: 100 },
    signal,
  });
}

/** 作出独立的画像数据使用同意；注册隐私同意和个性化开关不能替代该请求。 */
export function grantProfileDataConsent(privacyPolicyVersion: string): Promise<void> {
  return apiRequest<void>('/api/v1/auth/profile-data-consent', {
    body: { privacyPolicyVersion },
    method: 'PUT',
  });
}

/** 撤回画像数据使用同意；成功后调用方必须立即清除页面内存画像。 */
export function withdrawProfileDataConsent(): Promise<void> {
  return apiRequest<void>('/api/v1/auth/profile-data-consent', {
    method: 'DELETE',
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

/**
 * 新增当前用户的手工画像标签。
 *
 * 调用方必须传入读取到的画像版本和一次性幂等键；网络失败时由上层决定如何查询，
 * 本函数不会自动生成新幂等键或重发写请求。
 */
export function createMyTag(
  request: CreateProfileTagRequest,
  version: number,
  idempotencyKey: string,
): Promise<ProfileTag> {
  return apiRequest<ProfileTag>('/api/v1/profile/me/tags', {
    body: request,
    headers: {
      'Idempotency-Key': idempotencyKey,
      'If-Match': String(version),
    },
    method: 'POST',
  });
}

/** 更新当前用户手工标签的倾向、权重或状态。 */
export function updateMyTag(
  tagId: string,
  request: UpdateProfileTagRequest,
  version: number,
  idempotencyKey: string,
): Promise<ProfileTag> {
  return apiRequest<ProfileTag>(`/api/v1/profile/me/tags/${encodeURIComponent(tagId)}`, {
    body: request,
    headers: {
      'Idempotency-Key': idempotencyKey,
      'If-Match': String(version),
    },
    method: 'PUT',
  });
}

/** 软删除当前用户的画像标签。 */
export function deleteMyTag(tagId: string, version: number, idempotencyKey: string): Promise<void> {
  return apiRequest<void>(`/api/v1/profile/me/tags/${encodeURIComponent(tagId)}`, {
    allowEmptyResponse: true,
    headers: {
      'Idempotency-Key': idempotencyKey,
      'If-Match': String(version),
    },
    method: 'DELETE',
  });
}
