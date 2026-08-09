import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { describe, expect, it, vi } from 'vitest';

const layoutMocks = vi.hoisted(() => ({
  location: { pathname: '/', search: '' },
  navigate: vi.fn(),
}));

vi.mock('umi', () => ({
  Link: ({ children, to }: React.PropsWithChildren<{ to: string }>) => <a href={to}>{children}</a>,
  useLocation: () => layoutMocks.location,
  useNavigate: () => layoutMocks.navigate,
}));

vi.mock('antd', () => ({
  Avatar: () => <span>头像</span>,
  Dropdown: ({
    children,
    menu,
  }: {
    children: React.ReactNode;
    menu: {
      items: Array<{ key: string; label: string }>;
      onClick: (event: { key: string }) => void;
    };
  }) => (
    <div>
      {children}
      <div aria-label="城市选择菜单">
        {menu.items.map((item) => (
          <button key={item.key} type="button" onClick={() => menu.onClick({ key: item.key })}>
            {item.label}
          </button>
        ))}
      </div>
    </div>
  ),
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => ({ currentUser: null, status: 'anonymous' }),
}));

vi.mock('../../modules/auth/useLogout', () => ({
  useLogout: () => ({ handleLogout: vi.fn(), isLoggingOut: false }),
}));

import { DesktopTopBar } from './DesktopTopBar';

describe('DesktopTopBar', () => {
  it('展示受控城市菜单，并通过 URL 切换杭州', () => {
    layoutMocks.location = { pathname: '/', search: '' };
    layoutMocks.navigate.mockReset();
    render(<DesktopTopBar />);

    expect(screen.getByLabelText('当前城市：长沙，点击切换城市')).toHaveTextContent('长沙');
    fireEvent.click(screen.getByRole('button', { name: '杭州' }));
    expect(layoutMocks.navigate).toHaveBeenCalledWith('/?location=330100');
    expect(screen.queryByPlaceholderText('搜索电影、影院')).not.toBeInTheDocument();
  });
});
