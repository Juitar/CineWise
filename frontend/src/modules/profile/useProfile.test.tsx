import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { ProfilePage } from './api';
import { useProfile } from './useProfile';

const mocks = vi.hoisted(() => ({
  getMyProfile: vi.fn(),
  grantProfileDataConsent: vi.fn(),
  updateMyPersonalization: vi.fn(),
  withdrawProfileDataConsent: vi.fn(),
}));

vi.mock('./api', () => ({
  getMyProfile: mocks.getMyProfile,
  grantProfileDataConsent: mocks.grantProfileDataConsent,
  updateMyPersonalization: mocks.updateMyPersonalization,
  withdrawProfileDataConsent: mocks.withdrawProfileDataConsent,
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
    mocks.grantProfileDataConsent.mockReset();
    mocks.updateMyPersonalization.mockReset();
    mocks.withdrawProfileDataConsent.mockReset();
  });

  it('读取返回 202004 时清空画像且不自动重试', async () => {
    mocks.getMyProfile.mockRejectedValue(
      new ApiError('请先同意', { code: 202004, kind: 'HTTP', status: 403 }),
    );
    const { result } = renderHook(() => useProfile('2026-08-03'));

    await waitFor(() => expect(result.current.state).toBe('consent-required'));

    expect(result.current.profile).toBeNull();
    expect(result.current.notice).toBe('未开启画像数据使用');
    expect(result.current.consentEnabled).toBe(false);
    expect(result.current.consentStateKnown).toBe(true);
    expect(mocks.getMyProfile).toHaveBeenCalledOnce();
  });

  it('撤回后开关请求返回 202004 时删除此前已显示的数据', async () => {
    mocks.getMyProfile.mockResolvedValue(profile);
    mocks.updateMyPersonalization.mockRejectedValue(
      new ApiError('请先同意', { code: 202004, kind: 'HTTP', status: 403 }),
    );
    const { result } = renderHook(() => useProfile('2026-08-03'));
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
    const { result } = renderHook(() => useProfile('2026-08-03'));
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
    const { result } = renderHook(() => useProfile('2026-08-03'));

    await waitFor(() => expect(result.current.state).toBe('error'));
    expect(result.current.notice).toBe(message);
  });

  it('未同意用户开启画像后读取并显示最新画像', async () => {
    mocks.getMyProfile
      .mockRejectedValueOnce(new ApiError('请先同意', { code: 202004, kind: 'HTTP', status: 403 }))
      .mockResolvedValueOnce(profile);
    mocks.grantProfileDataConsent.mockResolvedValue(undefined);
    const { result } = renderHook(() => useProfile('2026-08-03'));
    await waitFor(() => expect(result.current.state).toBe('consent-required'));

    await act(async () => {
      await result.current.setConsentEnabled(true);
    });

    expect(mocks.grantProfileDataConsent).toHaveBeenCalledOnce();
    expect(mocks.grantProfileDataConsent).toHaveBeenCalledWith('2026-08-03');
    expect(mocks.getMyProfile).toHaveBeenCalledTimes(2);
    expect(result.current.state).toBe('ready');
    expect(result.current.consentEnabled).toBe(true);
    expect(result.current.profile).toEqual(profile);
  });

  it('撤回画像同意后立即清除标签和个性化结果', async () => {
    mocks.getMyProfile.mockResolvedValue(profile);
    mocks.withdrawProfileDataConsent.mockResolvedValue(undefined);
    const { result } = renderHook(() => useProfile('2026-08-03'));
    await waitFor(() => expect(result.current.state).toBe('ready'));

    await act(async () => {
      await result.current.setConsentEnabled(false);
    });

    expect(mocks.withdrawProfileDataConsent).toHaveBeenCalledOnce();
    expect(result.current.state).toBe('consent-required');
    expect(result.current.profile).toBeNull();
    expect(result.current.notice).toBe('未开启画像数据使用');
  });

  it('撤回结果未知时只读取画像 REST 恢复关闭状态', async () => {
    mocks.getMyProfile
      .mockResolvedValueOnce(profile)
      .mockRejectedValueOnce(new ApiError('请先同意', { code: 202004, kind: 'HTTP', status: 403 }));
    mocks.withdrawProfileDataConsent.mockRejectedValue(
      new ApiError('请求超时', { isResultUnknown: true, kind: 'TIMEOUT' }),
    );
    const { result } = renderHook(() => useProfile('2026-08-03'));
    await waitFor(() => expect(result.current.state).toBe('ready'));

    await act(async () => {
      await result.current.setConsentEnabled(false);
    });

    expect(mocks.withdrawProfileDataConsent).toHaveBeenCalledOnce();
    expect(mocks.getMyProfile).toHaveBeenCalledTimes(2);
    expect(result.current.state).toBe('consent-required');
    expect(result.current.profile).toBeNull();
  });

  it('同意状态冲突时只读取画像 REST 恢复最新开启状态', async () => {
    mocks.getMyProfile
      .mockRejectedValueOnce(new ApiError('请先同意', { code: 202004, kind: 'HTTP', status: 403 }))
      .mockResolvedValueOnce(profile);
    mocks.grantProfileDataConsent.mockRejectedValue(
      new ApiError('状态冲突', { code: 201011, kind: 'HTTP', status: 409 }),
    );
    const { result } = renderHook(() => useProfile('2026-08-03'));
    await waitFor(() => expect(result.current.state).toBe('consent-required'));

    await act(async () => {
      await result.current.setConsentEnabled(true);
    });

    expect(mocks.grantProfileDataConsent).toHaveBeenCalledOnce();
    expect(mocks.getMyProfile).toHaveBeenCalledTimes(2);
    expect(result.current.state).toBe('ready');
    expect(result.current.consentEnabled).toBe(true);
  });

  it('结果未知且读取失败时禁用未知状态而不重发撤回', async () => {
    mocks.getMyProfile
      .mockResolvedValueOnce(profile)
      .mockRejectedValueOnce(new ApiError('服务不可用', { kind: 'HTTP', status: 503 }));
    mocks.withdrawProfileDataConsent.mockRejectedValue(
      new ApiError('网络断开', { isResultUnknown: true, kind: 'NETWORK' }),
    );
    const { result } = renderHook(() => useProfile('2026-08-03'));
    await waitFor(() => expect(result.current.state).toBe('ready'));

    await act(async () => {
      await result.current.setConsentEnabled(false);
    });

    expect(mocks.withdrawProfileDataConsent).toHaveBeenCalledOnce();
    expect(result.current.state).toBe('error');
    expect(result.current.consentStateKnown).toBe(false);
    expect(result.current.notice).toBe('画像开关状态暂时无法确认，请刷新后重试');
  });

  it('同意请求提交期间阻止重复提交', async () => {
    let completeGrant: (() => void) | undefined;
    mocks.getMyProfile
      .mockRejectedValueOnce(new ApiError('请先同意', { code: 202004, kind: 'HTTP', status: 403 }))
      .mockResolvedValueOnce(profile);
    mocks.grantProfileDataConsent.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          completeGrant = resolve;
        }),
    );
    const { result } = renderHook(() => useProfile('2026-08-03'));
    await waitFor(() => expect(result.current.state).toBe('consent-required'));

    let firstRequest: Promise<void> | undefined;
    act(() => {
      firstRequest = result.current.setConsentEnabled(true);
      void result.current.setConsentEnabled(true);
    });

    expect(mocks.grantProfileDataConsent).toHaveBeenCalledOnce();
    await act(async () => {
      completeGrant?.();
      await firstRequest;
    });
    expect(result.current.state).toBe('ready');
  });

  it.each([
    [401, '登录状态已失效，请重新登录'],
    [422, '画像数据使用设置参数不正确'],
    [503, '画像数据使用设置暂时无法保存'],
  ])('画像同意写入返回 HTTP %s 时保留关闭状态并提示', async (status, message) => {
    mocks.getMyProfile.mockRejectedValue(
      new ApiError('请先同意', { code: 202004, kind: 'HTTP', status: 403 }),
    );
    mocks.grantProfileDataConsent.mockRejectedValue(
      new ApiError('设置失败', { kind: 'HTTP', status }),
    );
    const { result } = renderHook(() => useProfile('2026-08-03'));
    await waitFor(() => expect(result.current.state).toBe('consent-required'));

    await act(async () => {
      await result.current.setConsentEnabled(true);
    });

    expect(mocks.grantProfileDataConsent).toHaveBeenCalledOnce();
    expect(mocks.getMyProfile).toHaveBeenCalledOnce();
    expect(result.current.state).toBe('consent-required');
    expect(result.current.notice).toBe(message);
  });

  it('撤回提交期间阻止个性化开关并发写入', async () => {
    let completeWithdrawal: (() => void) | undefined;
    mocks.getMyProfile.mockResolvedValue(profile);
    mocks.withdrawProfileDataConsent.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          completeWithdrawal = resolve;
        }),
    );
    const { result } = renderHook(() => useProfile('2026-08-03'));
    await waitFor(() => expect(result.current.state).toBe('ready'));

    let withdrawalRequest: Promise<void> | undefined;
    act(() => {
      withdrawalRequest = result.current.setConsentEnabled(false);
      void result.current.setEnabled(false);
    });

    expect(mocks.withdrawProfileDataConsent).toHaveBeenCalledOnce();
    expect(mocks.updateMyPersonalization).not.toHaveBeenCalled();
    await act(async () => {
      completeWithdrawal?.();
      await withdrawalRequest;
    });
    expect(result.current.state).toBe('consent-required');
  });
});
