import { act, renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/ApiError';
import type { CurrentUser } from '../../modules/auth/types';
import { AuthProvider, useAuth } from './AuthProvider';

const authApiMocks = vi.hoisted(() => ({
  fetchCurrentUser: vi.fn(),
  submitLogout: vi.fn(),
  submitPasswordLogin: vi.fn(),
}));

vi.mock('../../modules/auth/api', () => authApiMocks);

const user: CurrentUser = {
  id: '1001',
  role: 'USER',
  nickname: '测试用户',
  emailMasked: 'u***@cinewise.test',
  emailVerified: true,
  status: 'NORMAL',
  privacyPolicyVersion: '2026-08-03',
};

function wrapper({ children }: PropsWithChildren) {
  return <AuthProvider>{children}</AuthProvider>;
}

describe('AuthProvider', () => {
  beforeEach(() => {
    authApiMocks.fetchCurrentUser.mockReset();
    authApiMocks.submitLogout.mockReset();
    authApiMocks.submitPasswordLogin.mockReset();
  });

  it('启动时只以 /auth/me 结果恢复当前用户', async () => {
    authApiMocks.fetchCurrentUser.mockResolvedValue(user);
    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('authenticated'));
    expect(result.current.currentUser).toEqual(user);
    expect(authApiMocks.fetchCurrentUser).toHaveBeenCalledWith(false);
  });

  it('启动时收到 401 进入匿名状态，不把它当成全局错误', async () => {
    authApiMocks.fetchCurrentUser.mockRejectedValue(
      new ApiError('session invalid', { kind: 'HTTP', status: 401, code: 201006 }),
    );
    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('anonymous'));
    expect(result.current.currentUser).toBeNull();
  });

  it('登录响应后再次查询 /auth/me，再更新全局身份', async () => {
    authApiMocks.fetchCurrentUser
      .mockRejectedValueOnce(
        new ApiError('session invalid', { kind: 'HTTP', status: 401, code: 201006 }),
      )
      .mockResolvedValueOnce(user);
    authApiMocks.submitPasswordLogin.mockResolvedValue(user);
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('anonymous'));

    await act(async () => {
      await result.current.login({
        clientRequestId: 'request-1',
        email: 'user@cinewise.test',
        password: 'Password1',
      });
    });

    expect(authApiMocks.submitPasswordLogin).toHaveBeenCalledOnce();
    expect(authApiMocks.fetchCurrentUser).toHaveBeenCalledTimes(2);
    expect(result.current.currentUser).toEqual(user);
  });

  it('登出请求失败时仍清理前端身份', async () => {
    authApiMocks.fetchCurrentUser.mockResolvedValue(user);
    authApiMocks.submitLogout.mockRejectedValue(new Error('network'));
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('authenticated'));

    await act(async () => {
      await expect(result.current.logout()).rejects.toThrow('network');
    });

    expect(result.current.status).toBe('anonymous');
    expect(result.current.currentUser).toBeNull();
  });
});
