import { cleanup, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CurrentUser } from '../../modules/auth/types';
import { DesktopSidebar } from './DesktopSidebar';
import { DesktopTopBar } from './DesktopTopBar';
import { MobileTabBar } from './MobileTabBar';

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
  auth: {
    currentUser: null as CurrentUser | null,
    status: 'anonymous' as 'anonymous' | 'authenticated',
  },
  location: { pathname: '/' },
}));

vi.mock('umi', () => ({
  Link: ({ children, to }: React.PropsWithChildren<{ to: string }>) => <a href={to}>{children}</a>,
  useLocation: () => mocks.location,
  useNavigate: () => vi.fn(),
}));

vi.mock('antd', () => ({
  Avatar: () => <span aria-hidden="true" />,
  Dropdown: ({
    children,
    menu,
  }: {
    children: React.ReactNode;
    menu: { items: Array<{ label?: React.ReactNode }> };
  }) => (
    <div>
      {children}
      <div>
        {menu.items.map((item, index) => (
          <React.Fragment key={index}>{item.label}</React.Fragment>
        ))}
      </div>
    </div>
  ),
  Input: () => <input />,
  Menu: ({ items }: { items: Array<{ icon?: React.ReactNode; label?: React.ReactNode }> }) => (
    <nav>
      {items.map((item, index) => (
        <React.Fragment key={index}>
          {item.icon}
          {item.label}
        </React.Fragment>
      ))}
    </nav>
  ),
}));

vi.mock('../../shared/components/icons/layout-icons', () => ({
  FilmIcon: () => <span data-testid="film-icon" />,
  HomeIcon: () => <span data-testid="home-icon" />,
  MapPinIcon: () => <span data-testid="map-pin-icon" />,
  OrderIcon: () => <span data-testid="order-icon" />,
  SearchIcon: () => <span data-testid="search-icon" />,
  UserIcon: () => <span data-testid="user-icon" />,
}));

vi.mock('antd-mobile', () => {
  const TabBar = ({ children }: React.PropsWithChildren) => <nav>{children}</nav>;
  TabBar.Item = ({ title }: { icon: React.ReactNode; title: string }) => (
    <button type="button">{title}</button>
  );
  return { TabBar };
});

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => mocks.auth,
}));

vi.mock('../../modules/auth/useLogout', () => ({
  useLogout: () => ({ handleLogout: vi.fn(), isLoggingOut: false }),
}));

describe('用户端订单导航', () => {
  beforeEach(() => {
    mocks.auth.currentUser = user;
    mocks.auth.status = 'authenticated';
    mocks.location.pathname = '/orders';
  });

  afterEach(cleanup);

  it('桌面侧边栏的首页和影片入口使用不同图标', () => {
    render(<DesktopSidebar />);

    expect(screen.getByTestId('home-icon')).toBeInTheDocument();
    expect(screen.getByTestId('film-icon')).toBeInTheDocument();
  });

  it('桌面侧边栏和用户菜单均不显示订单入口', () => {
    const { unmount } = render(<DesktopSidebar />);
    expect(screen.queryByRole('link', { name: '我的订单' })).not.toBeInTheDocument();
    unmount();

    render(<DesktopTopBar />);
    expect(screen.queryByRole('link', { name: '我的订单' })).not.toBeInTheDocument();
  });

  it('移动端底部导航不显示订单入口', () => {
    render(<MobileTabBar />);

    expect(screen.queryByRole('button', { name: '订单' })).not.toBeInTheDocument();
  });

  it('未登录用户也看不到公共导航中的订单操作', () => {
    mocks.auth.currentUser = null;
    mocks.auth.status = 'anonymous';

    const { unmount } = render(<DesktopSidebar />);
    expect(screen.queryByRole('link', { name: '我的订单' })).not.toBeInTheDocument();
    unmount();

    render(<MobileTabBar />);
    expect(screen.queryByRole('button', { name: '订单' })).not.toBeInTheDocument();
  });
});
