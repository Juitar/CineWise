import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CurrentUser } from '../../modules/auth/types';
import RequireAdmin from './RequireAdmin';
import RequireAuth from './RequireAuth';

const mocks = vi.hoisted(() => ({
  auth: {
    currentUser: null as CurrentUser | null,
    retrySessionCheck: vi.fn(),
    status: 'anonymous' as 'anonymous' | 'authenticated' | 'checking' | 'error',
  },
  location: { hash: '#detail', pathname: '/profile', search: '?tab=orders' },
}));

vi.mock('umi', () => ({
  Navigate: ({ to }: { replace?: boolean; to: string }) => <div data-testid="navigate">{to}</div>,
  Outlet: () => <div>受保护的子路由</div>,
  useLocation: () => mocks.location,
}));

vi.mock('./AuthProvider', () => ({
  useAuth: () => mocks.auth,
}));

const admin: CurrentUser = {
  id: '2001',
  role: 'ADMIN',
  nickname: '管理员',
  emailMasked: 'a***@cinewise.test',
  emailVerified: true,
  status: 'NORMAL',
  privacyPolicyVersion: '2026-08-03',
};

const user: CurrentUser = { ...admin, id: '1001', role: 'USER' };

function ProtectedContent({ children }: PropsWithChildren) {
  return <div>{children}</div>;
}

describe('认证路由守卫', () => {
  beforeEach(() => {
    mocks.auth.currentUser = null;
    mocks.auth.retrySessionCheck.mockReset();
    mocks.auth.status = 'anonymous';
    mocks.location.hash = '#detail';
    mocks.location.pathname = '/profile';
    mocks.location.search = '?tab=orders';
  });

  afterEach(cleanup);

  it('会话检查中显示等待状态', () => {
    mocks.auth.status = 'checking';
    render(<RequireAuth>个人中心</RequireAuth>);

    expect(screen.getByLabelText('正在检查登录状态')).toBeInTheDocument();
  });

  it('会话检查失败时允许用户主动重试', () => {
    mocks.auth.status = 'error';
    render(<RequireAuth>个人中心</RequireAuth>);

    fireEvent.click(screen.getByRole('button', { name: '重新检查' }));
    expect(mocks.auth.retrySessionCheck).toHaveBeenCalledOnce();
  });

  it('匿名用户跳转登录页并保留当前站内地址', () => {
    render(<RequireAuth>个人中心</RequireAuth>);

    expect(screen.getByTestId('navigate')).toHaveTextContent(
      '/login?returnUrl=%2Fprofile%3Ftab%3Dorders%23detail',
    );
  });

  it('匿名用户直接访问订单列表时不暴露受保护内容并保留订单回跳地址', () => {
    mocks.location.hash = '';
    mocks.location.pathname = '/orders';
    mocks.location.search = '';
    render(<RequireAuth>我的订单</RequireAuth>);

    expect(screen.getByTestId('navigate')).toHaveTextContent('/login?returnUrl=%2Forders');
    expect(screen.queryByText('我的订单')).not.toBeInTheDocument();
  });

  it('已登录用户可以进入用户页面', () => {
    mocks.auth.status = 'authenticated';
    mocks.auth.currentUser = user;
    render(
      <RequireAuth>
        <ProtectedContent>个人中心</ProtectedContent>
      </RequireAuth>,
    );

    expect(screen.getByText('个人中心')).toBeInTheDocument();
  });

  it('匿名用户访问管理页时跳转统一登录入口', () => {
    mocks.location.hash = '';
    mocks.location.pathname = '/admin/orders';
    mocks.location.search = '';
    render(<RequireAdmin>订单管理</RequireAdmin>);

    expect(screen.getByTestId('navigate')).toHaveTextContent('/login?returnUrl=%2Fadmin%2Forders');
  });

  it('普通用户访问管理页时进入 403', () => {
    mocks.auth.status = 'authenticated';
    mocks.auth.currentUser = user;
    render(<RequireAdmin>订单管理</RequireAdmin>);

    expect(screen.getByTestId('navigate')).toHaveTextContent('/403');
  });

  it('管理员可以进入管理页面', () => {
    mocks.auth.status = 'authenticated';
    mocks.auth.currentUser = admin;
    render(<RequireAdmin>订单管理</RequireAdmin>);

    expect(screen.getByText('订单管理')).toBeInTheDocument();
  });

  it('Umi 父路由模式下通过 Outlet 渲染受保护页面', () => {
    mocks.auth.status = 'authenticated';
    mocks.auth.currentUser = admin;
    render(<RequireAdmin />);

    expect(screen.getByText('受保护的子路由')).toBeInTheDocument();
  });
});
