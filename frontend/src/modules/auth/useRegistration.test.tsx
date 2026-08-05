import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { CurrentUser } from './types';
import { useRegistration } from './useRegistration';

const authMocks = vi.hoisted(() => ({
  recoverSession: vi.fn(),
}));

const apiMocks = vi.hoisted(() => ({
  sendEmailCode: vi.fn(),
  submitRegistration: vi.fn(),
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => authMocks,
}));

vi.mock('./api', () => apiMocks);

const user: CurrentUser = {
  id: '1001',
  role: 'USER',
  nickname: '测试用户',
  emailMasked: 'u***@cinewise.test',
  emailVerified: true,
  status: 'NORMAL',
  privacyPolicyVersion: '2026-08-03',
};

const values = {
  code: '123456',
  email: ' USER@CINEWISE.TEST ',
  inviteCode: 'private-invite',
  nickname: ' 测试用户 ',
  password: 'Password1',
  privacyPolicyVersion: '2026-08-03',
};

describe('useRegistration', () => {
  beforeEach(() => {
    authMocks.recoverSession.mockReset();
    apiMocks.sendEmailCode.mockReset();
    apiMocks.submitRegistration.mockReset();
    vi.stubGlobal('crypto', { randomUUID: () => 'registration-uuid' });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('发送 REGISTER 验证码并按服务端秒数冷却', async () => {
    vi.useFakeTimers();
    apiMocks.sendEmailCode.mockResolvedValue({ cooldownSeconds: 60, expiresInSeconds: 300 });
    const { result } = renderHook(() => useRegistration());

    await act(async () => {
      await result.current.requestCode(' user@cinewise.test ');
    });

    expect(apiMocks.sendEmailCode).toHaveBeenCalledWith({
      email: 'user@cinewise.test',
      purpose: 'REGISTER',
    });
    expect(result.current.cooldownSeconds).toBe(60);
    expect(result.current.isSendCodeDisabled).toBe(true);

    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.cooldownSeconds).toBe(59);
  });

  it('生成一次 clientRequestId、提交确认字段并通过 /auth/me 恢复身份', async () => {
    apiMocks.submitRegistration.mockResolvedValue(user);
    authMocks.recoverSession.mockResolvedValue(user);
    const { result } = renderHook(() => useRegistration());

    let submission;
    await act(async () => {
      submission = await result.current.submit(values);
    });

    expect(apiMocks.submitRegistration).toHaveBeenCalledWith({
      clientRequestId: 'registration-uuid',
      code: '123456',
      email: 'USER@CINEWISE.TEST',
      inviteCode: 'private-invite',
      nickname: '测试用户',
      password: 'Password1',
      privacyAccepted: true,
      privacyPolicyVersion: '2026-08-03',
    });
    expect(authMocks.recoverSession).toHaveBeenCalledOnce();
    expect(submission).toEqual({ clearSensitiveInputs: true, user });
  });

  it('按错误码显示邀请码不可用且不会恢复会话', async () => {
    apiMocks.submitRegistration.mockRejectedValue(
      new ApiError('invite unavailable', { kind: 'HTTP', status: 422, code: 201004 }),
    );
    const { result } = renderHook(() => useRegistration());

    await act(async () => {
      await result.current.submit(values);
    });

    expect(result.current.registrationErrorMessage).toBe('邀请码不可用，请检查后重试');
    expect(result.current.registrationStatus).toBe('error');
    expect(authMocks.recoverSession).not.toHaveBeenCalled();
  });

  it('注册响应丢失时只查询 /auth/me，不重新发送注册请求', async () => {
    apiMocks.submitRegistration.mockRejectedValue(
      new ApiError('timeout', { kind: 'TIMEOUT', isResultUnknown: true }),
    );
    authMocks.recoverSession.mockRejectedValue(new Error('offline'));
    const { result } = renderHook(() => useRegistration());

    await act(async () => {
      await result.current.submit(values);
    });

    expect(apiMocks.submitRegistration).toHaveBeenCalledOnce();
    expect(authMocks.recoverSession).toHaveBeenCalledOnce();
    expect(result.current.registrationStatus).toBe('result-unknown');
    expect(result.current.isSubmitDisabled).toBe(true);

    authMocks.recoverSession.mockResolvedValue(user);
    await act(async () => {
      await result.current.retryRecovery();
    });
    expect(apiMocks.submitRegistration).toHaveBeenCalledOnce();
    expect(authMocks.recoverSession).toHaveBeenCalledTimes(2);
  });

  it('发送结果未知时提示检查邮箱并启动保护冷却', async () => {
    apiMocks.sendEmailCode.mockRejectedValue(
      new ApiError('network', { kind: 'NETWORK', isResultUnknown: true }),
    );
    const { result } = renderHook(() => useRegistration());

    await act(async () => {
      await result.current.requestCode('user@cinewise.test');
    });

    expect(result.current.cooldownSeconds).toBe(60);
    expect(result.current.sendCodeMessage).toContain('先检查邮箱');
    expect(apiMocks.sendEmailCode).toHaveBeenCalledOnce();
  });
});
