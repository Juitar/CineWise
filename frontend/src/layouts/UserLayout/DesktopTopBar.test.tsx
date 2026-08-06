import { render, screen } from '@testing-library/react';
import React from 'react';
import { describe, expect, it, vi } from 'vitest';

vi.mock('umi', () => ({
  Link: ({ children, to }: React.PropsWithChildren<{ to: string }>) => <a href={to}>{children}</a>,
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => ({ currentUser: null, status: 'anonymous' }),
}));

vi.mock('../../modules/auth/useLogout', () => ({
  useLogout: () => ({ handleLogout: vi.fn(), isLoggingOut: false }),
}));

import { DesktopTopBar } from './DesktopTopBar';

describe('DesktopTopBar', () => {
  it('没有城市解析接口时只显示默认长沙且不伪造切换菜单', () => {
    render(<DesktopTopBar />);

    expect(screen.getByLabelText('当前城市：长沙')).toHaveTextContent('长沙');
    expect(screen.queryByText('杭州')).not.toBeInTheDocument();
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});
