import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useLogout } from './useLogout';

const mocks = vi.hoisted(() => ({
  logout: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock('umi', () => ({
  useNavigate: () => mocks.navigate,
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => ({ logout: mocks.logout }),
}));

describe('useLogout', () => {
  beforeEach(() => {
    mocks.logout.mockReset();
    mocks.navigate.mockReset();
  });

  it('退出提交期间忽略重复操作', async () => {
    let finishLogout: (() => void) | undefined;
    mocks.logout.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finishLogout = resolve;
        }),
    );
    const { result } = renderHook(() => useLogout());

    let firstLogout: Promise<void> | undefined;
    await act(async () => {
      firstLogout = result.current.handleLogout();
      await result.current.handleLogout();
    });

    expect(mocks.logout).toHaveBeenCalledOnce();
    expect(result.current.isLoggingOut).toBe(true);

    await act(async () => {
      finishLogout?.();
      await firstLogout;
    });

    expect(mocks.navigate).toHaveBeenCalledWith('/login', { replace: true });
    expect(result.current.isLoggingOut).toBe(false);
  });

  it('退出请求失败后仍离开私有页面', async () => {
    mocks.logout.mockRejectedValue(new Error('network'));
    const { result } = renderHook(() => useLogout());

    await act(async () => {
      await result.current.handleLogout();
    });

    expect(mocks.navigate).toHaveBeenCalledWith('/login', { replace: true });
    expect(result.current.isLoggingOut).toBe(false);
  });
});
