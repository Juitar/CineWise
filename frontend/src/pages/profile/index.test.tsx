import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CurrentUser } from '../../modules/auth/types';
import ProfilePage from './index';

const user: CurrentUser = {
  id: '1001',
  role: 'USER',
  nickname: '演示用户',
  emailMasked: 'u***@cinewise.test',
  emailVerified: true,
  status: 'NORMAL',
  privacyPolicyVersion: '2026-08-03',
};

const mocks = vi.hoisted(() => ({
  auth: { currentUser: null as CurrentUser | null },
  logout: { handleLogout: vi.fn(), isLoggingOut: false },
}));

vi.mock('umi', () => ({
  Link: ({ children, to, ...props }: React.PropsWithChildren<{ to: string }>) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => mocks.auth,
}));

vi.mock('../../modules/auth/useLogout', () => ({
  useLogout: () => mocks.logout,
}));

describe('ProfilePage', () => {
  beforeEach(() => {
    mocks.auth.currentUser = user;
    mocks.logout.handleLogout.mockReset().mockResolvedValue(undefined);
    mocks.logout.isLoggingOut = false;
  });

  afterEach(cleanup);

  it('展示认证接口提供的账号摘要和隐私入口', () => {
    render(<ProfilePage />);

    expect(screen.getByRole('heading', { level: 1, name: '个人中心' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '演示用户' })).toBeInTheDocument();
    expect(screen.getByText('u***@cinewise.test')).toBeInTheDocument();
    expect(screen.getByText('已验证')).toBeInTheDocument();
    expect(screen.getByText('2026-08-03')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看隐私说明' })).toHaveAttribute('href', '/privacy');
  });

  it('不重复显示订单入口，也不展示未接入的记录和画像数据', () => {
    render(<ProfilePage />);

    expect(screen.queryByRole('link', { name: '我的订单' })).not.toBeInTheDocument();
    expect(screen.queryByText('我的观影记录')).not.toBeInTheDocument();
    expect(screen.queryByText('AI 观影画像')).not.toBeInTheDocument();
    expect(screen.queryByText('星际穿越')).not.toBeInTheDocument();
  });

  it('通过共享退出 Hook 执行退出并显示提交中状态', () => {
    const { rerender } = render(<ProfilePage />);

    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));
    expect(mocks.logout.handleLogout).toHaveBeenCalledOnce();

    mocks.logout.isLoggingOut = true;
    rerender(<ProfilePage />);
    expect(screen.getByRole('button', { name: '正在退出' })).toBeDisabled();
  });

  it('用户摘要缺失时不展示默认资料', () => {
    mocks.auth.currentUser = null;
    render(<ProfilePage />);

    expect(screen.getByRole('alert')).toHaveTextContent('暂时无法读取个人资料');
    expect(screen.queryByRole('link', { name: '我的订单' })).not.toBeInTheDocument();
    expect(screen.queryByText('演示用户')).not.toBeInTheDocument();
  });
});
