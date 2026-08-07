import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { ProfilePage } from './api';
import { useProfile } from './useProfile';

const mocks = vi.hoisted(() => ({
  getMyProfile: vi.fn(),
  updateMyPersonalization: vi.fn(),
}));

vi.mock('./api', () => ({
  getMyProfile: mocks.getMyProfile,
  updateMyPersonalization: mocks.updateMyPersonalization,
}));

const profile: ProfilePage = {
  preference: { enabled: true, updatedAt: '2026-08-07T00:00:00Z', version: 3 },
  tags: [
    {
      confidence: 1,
      expiresAt: null,
      id: '1001',
      polarity: 'LIKE',
      source: 'MANUAL',
      status: 'ACTIVE',
      type: 'MOVIE_GENRE',
      updatedAt: '2026-08-07T00:00:00Z',
      value: '科幻',
      version: 0,
      weight: 0.9,
    },
  ],
  total: 1,
};

describe('useProfile', () => {
  beforeEach(() => {
    mocks.getMyProfile.mockReset();
    mocks.updateMyPersonalization.mockReset();
  });

  it('读取返回 202004 时清空画像且不自动重试', async () => {
    mocks.getMyProfile.mockRejectedValue(
      new ApiError('请先同意', { code: 202004, kind: 'HTTP', status: 403 }),
    );
    const { result } = renderHook(() => useProfile());

    await waitFor(() => expect(result.current.state).toBe('consent-required'));

    expect(result.current.profile).toBeNull();
    expect(result.current.notice).toBe('未开启画像数据使用');
    expect(mocks.getMyProfile).toHaveBeenCalledOnce();
  });

  it('撤回后开关请求返回 202004 时删除此前已显示的数据', async () => {
    mocks.getMyProfile.mockResolvedValue(profile);
    mocks.updateMyPersonalization.mockRejectedValue(
      new ApiError('请先同意', { code: 202004, kind: 'HTTP', status: 403 }),
    );
    const { result } = renderHook(() => useProfile());
    await waitFor(() => expect(result.current.state).toBe('ready'));

    await act(async () => {
      await result.current.setEnabled(false);
    });

    expect(result.current.profile).toBeNull();
    expect(result.current.notice).toBe('未开启画像数据使用');
    expect(mocks.updateMyPersonalization).toHaveBeenCalledOnce();
  });

  it('409 只刷新最新状态而不重试原写请求', async () => {
    const latest = {
      ...profile,
      preference: { ...profile.preference, enabled: false, version: 4 },
    };
    mocks.getMyProfile.mockResolvedValueOnce(profile).mockResolvedValueOnce(latest);
    mocks.updateMyPersonalization.mockRejectedValue(
      new ApiError('版本冲突', { code: 202002, kind: 'HTTP', status: 409 }),
    );
    const { result } = renderHook(() => useProfile());
    await waitFor(() => expect(result.current.state).toBe('ready'));

    await act(async () => {
      await result.current.setEnabled(false);
    });

    expect(mocks.updateMyPersonalization).toHaveBeenCalledOnce();
    expect(mocks.getMyProfile).toHaveBeenCalledTimes(2);
    expect(result.current.profile?.preference).toEqual(latest.preference);
  });

  it.each([
    [401, '登录状态已失效，请重新登录'],
    [403, '当前账号无权读取画像数据'],
    [422, '画像查询参数不正确'],
    [503, '画像服务暂时不可用'],
  ])('按 HTTP %s 显示明确读取结果', async (status, message) => {
    mocks.getMyProfile.mockRejectedValue(new ApiError('读取失败', { kind: 'HTTP', status }));
    const { result } = renderHook(() => useProfile());

    await waitFor(() => expect(result.current.state).toBe('error'));
    expect(result.current.notice).toBe(message);
  });
});
