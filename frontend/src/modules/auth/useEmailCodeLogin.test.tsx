import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { CurrentUser } from './types';
import { useEmailCodeLogin } from './useEmailCodeLogin';

const authMocks = vi.hoisted(() => ({
  loginWithEmailCode: vi.fn(),
  recoverSession: vi.fn(),
}));

const apiMocks = vi.hoisted(() => ({
  sendEmailCode: vi.fn(),
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

describe('useEmailCodeLogin', () => {
  beforeEach(() => {
    authMocks.loginWithEmailCode.mockReset();
    authMocks.recoverSession.mockReset();
    apiMocks.sendEmailCode.mockReset();
    vi.stubGlobal('crypto', { randomUUID: () => 'email-login-uuid' });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('发送 LOGIN 验证码并按服务端秒数冷却', async () => {
    vi.useFakeTimers();
    apiMocks.sendEmailCode.mockResolvedValue({ cooldownSeconds: 60, expiresInSeconds: 300 });
    const { result } = renderHook(() => useEmailCodeLogin());

    await act(async () => {
      await result.current.requestCode(' user@cinewise.test ');
    });

    expect(apiMocks.sendEmailCode).toHaveBeenCalledWith({
      email: 'user@cinewise.test',
      purpose: 'LOGIN',
    });
    expect(result.current.cooldownSeconds).toBe(60);
    expect(result.current.isSendCodeDisabled).toBe(true);
    expect(result.current.sendCodeMessage).toBe('请检查邮箱');
  });

  it('提交验证码登录并返回当前用户', async () => {
    authMocks.loginWithEmailCode.mockResolvedValue(user);
    const { result } = renderHook(() => useEmailCodeLogin());

    let submission;
    await act(async () => {
      submission = await result.current.submit(' USER@CINEWISE.TEST ', '123456');
    });

    expect(authMocks.loginWithEmailCode).toHaveBeenCalledWith({
      clientRequestId: 'email-login-uuid',
      code: '123456',
      email: 'USER@CINEWISE.TEST',
    });
    expect(submission).toEqual({ clearCode: true, user });
  });

  it('验证码无效时显示统一提示并允许重新提交', async () => {
    authMocks.loginWithEmailCode.mockRejectedValue(
      new ApiError('invalid code', { kind: 'HTTP', status: 422, code: 201002 }),
    );
    const { result } = renderHook(() => useEmailCodeLogin());

    await act(async () => {
      await result.current.submit('user@cinewise.test', '123456');
    });

    expect(result.current.errorMessage).toBe('验证码无效或已过期，请重新获取');
    expect(result.current.status).toBe('error');
    expect(result.current.isSubmitDisabled).toBe(false);
  });

  it('登录结果未知时只查询 /auth/me，不重发验证码登录请求', async () => {
    authMocks.loginWithEmailCode.mockRejectedValue(
      new ApiError('timeout', { kind: 'TIMEOUT', isResultUnknown: true }),
    );
    authMocks.recoverSession.mockRejectedValue(new Error('offline'));
    const { result } = renderHook(() => useEmailCodeLogin());

    await act(async () => {
      await result.current.submit('user@cinewise.test', '123456');
    });

    expect(authMocks.loginWithEmailCode).toHaveBeenCalledOnce();
    expect(authMocks.recoverSession).toHaveBeenCalledOnce();
    expect(result.current.status).toBe('result-unknown');
    expect(result.current.isSubmitDisabled).toBe(true);

    authMocks.recoverSession.mockResolvedValue(user);
    await act(async () => {
      await result.current.retryRecovery();
    });
    expect(authMocks.loginWithEmailCode).toHaveBeenCalledOnce();
    expect(authMocks.recoverSession).toHaveBeenCalledTimes(2);
  });

  it('发送结果未知时进入保护冷却，不自动重发', async () => {
    apiMocks.sendEmailCode.mockRejectedValue(
      new ApiError('network', { kind: 'NETWORK', isResultUnknown: true }),
    );
    const { result } = renderHook(() => useEmailCodeLogin());

    await act(async () => {
      await result.current.requestCode('user@cinewise.test');
    });

    expect(result.current.cooldownSeconds).toBe(60);
    expect(result.current.sendCodeMessage).toContain('先检查邮箱');
    expect(apiMocks.sendEmailCode).toHaveBeenCalledOnce();
  });
});
