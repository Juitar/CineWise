import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { CurrentUser } from './types';
import { usePasswordLogin } from './usePasswordLogin';

const authMocks = vi.hoisted(() => ({
  login: vi.fn(),
  recoverSession: vi.fn(),
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => ({
    login: authMocks.login,
    recoverSession: authMocks.recoverSession,
  }),
}));

const user: CurrentUser = {
  id: '1001',
  role: 'USER',
  nickname: '测试用户',
  emailMasked: 'u***@cinewise.test',
  emailVerified: true,
  status: 'NORMAL',
  privacyPolicyVersion: '2026-08-03',
};

describe('usePasswordLogin', () => {
  beforeEach(() => {
    authMocks.login.mockReset();
    authMocks.recoverSession.mockReset();
    vi.stubGlobal('crypto', { randomUUID: () => 'request-uuid' });
  });

  it('生成一次 clientRequestId 并提交规范化邮箱', async () => {
    authMocks.login.mockResolvedValue(user);
    const { result } = renderHook(() => usePasswordLogin());

    let submission;
    await act(async () => {
      submission = await result.current.submit(' USER@CINEWISE.TEST ', 'Password1');
    });

    expect(authMocks.login).toHaveBeenCalledWith({
      clientRequestId: 'request-uuid',
      email: 'USER@CINEWISE.TEST',
      password: 'Password1',
    });
    expect(submission).toEqual({ clearPassword: true, user });
  });

  it('登录响应丢失时只查询 /auth/me，不重发密码', async () => {
    authMocks.login.mockRejectedValue(
      new ApiError('timeout', { kind: 'TIMEOUT', isResultUnknown: true }),
    );
    authMocks.recoverSession.mockResolvedValue(user);
    const { result } = renderHook(() => usePasswordLogin());

    let submission;
    await act(async () => {
      submission = await result.current.submit('user@cinewise.test', 'Password1');
    });

    expect(authMocks.login).toHaveBeenCalledTimes(1);
    expect(authMocks.recoverSession).toHaveBeenCalledTimes(1);
    expect(submission).toEqual({ clearPassword: true, user });
  });

  it('恢复查询仍失败时保持结果未知，只允许重新查询', async () => {
    authMocks.login.mockRejectedValue(
      new ApiError('network', { kind: 'NETWORK', isResultUnknown: true }),
    );
    authMocks.recoverSession.mockRejectedValue(new Error('offline'));
    const { result } = renderHook(() => usePasswordLogin());

    await act(async () => {
      await result.current.submit('user@cinewise.test', 'Password1');
    });

    expect(result.current.status).toBe('result-unknown');
    expect(result.current.isSubmitDisabled).toBe(true);
    expect(result.current.errorMessage).toContain('重新查询');
  });

  it('普通登录失败后清除密码并允许用户重新提交', async () => {
    authMocks.login
      .mockRejectedValueOnce(
        new ApiError('bad credentials', { kind: 'HTTP', status: 401, code: 201001 }),
      )
      .mockResolvedValueOnce(user);
    const { result } = renderHook(() => usePasswordLogin());

    let firstSubmission;
    await act(async () => {
      firstSubmission = await result.current.submit('user@cinewise.test', 'WrongPassword1');
    });

    expect(firstSubmission).toEqual({ clearPassword: true, user: null });
    expect(result.current.status).toBe('error');
    expect(result.current.errorMessage).toBe('账号或密码不正确');

    await act(async () => {
      await result.current.submit('user@cinewise.test', 'Password1');
    });
    expect(authMocks.login).toHaveBeenCalledTimes(2);
  });

  it('结果未知后确认仍为匿名时清除密码并恢复主动提交', async () => {
    authMocks.login.mockRejectedValue(
      new ApiError('timeout', { kind: 'TIMEOUT', isResultUnknown: true }),
    );
    authMocks.recoverSession.mockResolvedValue(null);
    const { result } = renderHook(() => usePasswordLogin());

    let submission;
    await act(async () => {
      submission = await result.current.submit('user@cinewise.test', 'Password1');
    });

    expect(submission).toEqual({ clearPassword: true, user: null });
    expect(result.current.status).toBe('error');
    expect(result.current.isSubmitDisabled).toBe(false);
    expect(result.current.errorMessage).toContain('重新输入密码');
  });

  it('同一次请求未结束前忽略重复提交', async () => {
    let resolveLogin: ((value: CurrentUser) => void) | undefined;
    authMocks.login.mockImplementation(
      () =>
        new Promise<CurrentUser>((resolve) => {
          resolveLogin = resolve;
        }),
    );
    const { result } = renderHook(() => usePasswordLogin());

    let firstRequest: Promise<unknown> | undefined;
    let duplicateResult;
    await act(async () => {
      firstRequest = result.current.submit('user@cinewise.test', 'Password1');
      duplicateResult = await result.current.submit('user@cinewise.test', 'Password1');
    });

    expect(duplicateResult).toEqual({ clearPassword: false, user: null });
    expect(authMocks.login).toHaveBeenCalledOnce();

    await act(async () => {
      resolveLogin?.(user);
      await firstRequest;
    });
  });
});
