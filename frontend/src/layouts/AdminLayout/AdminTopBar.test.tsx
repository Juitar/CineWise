import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  logout: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock('umi', () => ({
  useNavigate: () => mocks.navigate,
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => ({
    currentUser: { nickname: '影院管理员' },
    logout: mocks.logout,
  }),
}));

import { AdminTopBar } from './AdminTopBar';

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

describe('AdminTopBar', () => {
  it('展示真实管理员昵称且不伪造通知数量', () => {
    const { container } = render(<AdminTopBar />);

    expect(screen.getByText('影院管理员')).toBeInTheDocument();
    expect(container.querySelector('.ant-avatar')).toBeInTheDocument();
    expect(container.querySelector('.arrow-down')).toBeInTheDocument();
    expect(container.querySelector('.admin-notification')).not.toBeInTheDocument();
    expect(screen.queryByText('3')).not.toBeInTheDocument();
  });
});
