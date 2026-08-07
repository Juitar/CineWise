import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getMyProfile, updateMyPersonalization } from './api';

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
});
