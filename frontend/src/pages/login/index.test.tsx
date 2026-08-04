import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as umi from 'umi';

import LoginPage from './index';

// Mock umi hooks
vi.mock('umi', () => ({
  useLocation: vi.fn(() => ({ pathname: '/login', search: '' })),
  Link: ({ children, to, ...props }: React.PropsWithChildren<{ to: string }>) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

// Mock matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(), // Deprecated
    removeListener: vi.fn(), // Deprecated
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

describe('LoginPage Component', () => {
  beforeEach(() => {
    vi.mocked(umi.useLocation).mockReturnValue({
      pathname: '/login',
      search: '',
      state: null,
      key: 'test',
      hash: '',
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders standard login title and text by default', () => {
    vi.mocked(umi.useLocation).mockReturnValue({
      pathname: '/login',
      search: '',
      state: null,
      key: 'test',
      hash: '',
    });
    render(<LoginPage />);

    expect(screen.getByRole('heading', { name: '登录' })).toBeInTheDocument();
    expect(screen.getByText('登录后继续购票、查看订单与个性化观影服务')).toBeInTheDocument();
  });

  it('renders admin login title and text for /admin/login', () => {
    vi.mocked(umi.useLocation).mockReturnValue({
      pathname: '/admin/login',
      search: '',
      state: null,
      key: 'test',
      hash: '',
    });
    render(<LoginPage />);

    expect(screen.getByRole('heading', { name: '管理登录' })).toBeInTheDocument();
    expect(screen.getByText('欢迎使用妙语购票管理后台')).toBeInTheDocument();
  });

  it('contains email and password inputs with correct accessibility labels', () => {
    render(<LoginPage />);

    const emailInput = screen.getByRole('textbox', { name: '邮箱' });
    expect(emailInput).toHaveAttribute('type', 'email');

    // Password input is not a standard textbox, so we query by LabelText
    const passwordInput = screen.getByLabelText('密码');
    expect(passwordInput).toHaveAttribute('type', 'password');
  });

  it('toggles password visibility correctly', () => {
    render(<LoginPage />);

    const passwordInput = screen.getByLabelText('密码');
    expect(passwordInput).toHaveAttribute('type', 'password');

    const toggleButton = screen.getByRole('button', { name: '显示密码' });
    fireEvent.click(toggleButton);

    expect(passwordInput).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: '隐藏密码' })).toBeInTheDocument();
  });

  it('shows the user registration entry only on the user login page', () => {
    render(<LoginPage />);

    expect(screen.queryByText('验证码登录')).not.toBeInTheDocument();
    expect(screen.queryByText('获取验证码')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '立即注册' })).toHaveAttribute('href', '/register');
    expect(screen.queryByText('忘记密码')).not.toBeInTheDocument();
    expect(screen.queryByText('用户协议')).not.toBeInTheDocument();
  });

  it('does not show a registration entry on the administrator login page', () => {
    vi.mocked(umi.useLocation).mockReturnValue({
      pathname: '/admin/login',
      search: '',
      state: null,
      key: 'test',
      hash: '',
    });
    render(<LoginPage />);

    expect(screen.queryByRole('link', { name: '立即注册' })).not.toBeInTheDocument();
  });

  it('prevents default form submission without calling any real api', () => {
    render(<LoginPage />);
    const submitButton = screen.getByRole('button', { name: /登\s*录/ });

    const form = submitButton.closest('form')!;
    const isNotPrevented = fireEvent.submit(form);

    // fireEvent returns false if preventDefault was called
    expect(isNotPrevented).toBe(false);
  });
});
