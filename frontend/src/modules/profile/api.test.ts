import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createMyTag,
  deleteMyTag,
  getMyProfile,
  grantProfileDataConsent,
  updateMyTag,
  updateMyPersonalization,
  withdrawProfileDataConsent,
} from './api';

const mocks = vi.hoisted(() => ({ apiRequest: vi.fn() }));

vi.mock('../../shared/api/client', () => ({ apiRequest: mocks.apiRequest }));

describe('profile api', () => {
  beforeEach(() => {
    mocks.apiRequest.mockReset();
  });

  it('通过本人标签 REST 一次读取页面数据', async () => {
    mocks.apiRequest.mockResolvedValue({ preference: {}, tags: [], total: 0 });

    await getMyProfile();

    expect(mocks.apiRequest).toHaveBeenCalledWith('/api/v1/profile/me/tags', {
      cache: 'no-store',
      query: { page: 1, size: 100 },
      signal: undefined,
    });
  });

  it('更新开关时携带画像版本和幂等键', async () => {
    mocks.apiRequest.mockResolvedValue({ enabled: false, updatedAt: '', version: 4 });

    await updateMyPersonalization(false, 3, 'request-1');

    expect(mocks.apiRequest).toHaveBeenCalledWith('/api/v1/profile/me/personalization', {
      body: { enabled: false },
      headers: { 'Idempotency-Key': 'request-1', 'If-Match': '3' },
      method: 'PUT',
    });
  });

  it('开启画像数据使用时提交当前隐私政策版本', async () => {
    mocks.apiRequest.mockResolvedValue(undefined);

    await grantProfileDataConsent('2026-08-03');

    expect(mocks.apiRequest).toHaveBeenCalledWith('/api/v1/auth/profile-data-consent', {
      body: { privacyPolicyVersion: '2026-08-03' },
      method: 'PUT',
    });
  });

  it('关闭画像数据使用时调用撤回接口', async () => {
    mocks.apiRequest.mockResolvedValue(undefined);

    await withdrawProfileDataConsent();

    expect(mocks.apiRequest).toHaveBeenCalledWith('/api/v1/auth/profile-data-consent', {
      method: 'DELETE',
    });
  });

  it('新增标签时携带请求体、画像版本和幂等键', async () => {
    mocks.apiRequest.mockResolvedValue({ id: '1001' });

    await createMyTag(
      { type: 'MOVIE_GENRE', value: '科幻', polarity: 'LIKE', weight: 1 },
      3,
      'request-create',
    );

    expect(mocks.apiRequest).toHaveBeenCalledWith('/api/v1/profile/me/tags', {
      body: { type: 'MOVIE_GENRE', value: '科幻', polarity: 'LIKE', weight: 1 },
      headers: { 'Idempotency-Key': 'request-create', 'If-Match': '3' },
      method: 'POST',
    });
  });

  it('更新和删除标签使用标签 ID 和画像版本', async () => {
    mocks.apiRequest.mockResolvedValue({ id: '1001' });

    await updateMyTag('1001', { polarity: 'DISLIKE' }, 4, 'request-update');
    await deleteMyTag('1001', 5, 'request-delete');

    expect(mocks.apiRequest).toHaveBeenNthCalledWith(1, '/api/v1/profile/me/tags/1001', {
      body: { polarity: 'DISLIKE' },
      headers: { 'Idempotency-Key': 'request-update', 'If-Match': '4' },
      method: 'PUT',
    });
    expect(mocks.apiRequest).toHaveBeenNthCalledWith(2, '/api/v1/profile/me/tags/1001', {
      allowEmptyResponse: true,
      headers: { 'Idempotency-Key': 'request-delete', 'If-Match': '5' },
      method: 'DELETE',
    });
  });
});
