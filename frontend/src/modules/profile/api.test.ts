import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getMyProfile,
  grantProfileDataConsent,
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
});
