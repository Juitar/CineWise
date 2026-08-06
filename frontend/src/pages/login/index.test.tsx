import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CurrentUser } from '../../modules/auth/types';
import LoginPage from './index';

const mocks = vi.hoisted(() => ({
  auth: {
    currentUser: null as CurrentUser | null,
    status: 'anonymous' as 'anonymous' | 'authenticated' | 'checking' | 'error',
  },
  login: {
    clearFeedback: vi.fn(),
    errorMessage: null as string | null,
    isSubmitDisabled: false,
    retryRecovery: vi.fn(),
    status: 'idle' as 'error' | 'idle' | 'recovering' | 'result-unknown' | 'submitting',
    submit: vi.fn(),
  },
  emailCodeLogin: {
    clearFeedback: vi.fn(),
    cooldownSeconds: 0,
    errorMessage: null as string | null,
    isSendCodeDisabled: false,
    isSubmitDisabled: false,
    requestCode: vi.fn(),
    retryRecovery: vi.fn(),
    sendCodeMessage: null as string | null,
    sendCodeStatus: 'idle' as 'error' | 'idle' | 'sending',
    status: 'idle' as 'error' | 'idle' | 'recovering' | 'result-unknown' | 'submitting',
    submit: vi.fn(),
  },
  navigate: vi.fn(),
  searchParams: new URLSearchParams(),
}));

vi.mock('umi', () => ({
  Link: ({ children, to, ...props }: React.PropsWithChildren<{ to: string }>) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useNavigate: () => mocks.navigate,
  useSearchParams: () => [mocks.searchParams, vi.fn()],
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => mocks.auth,
}));

vi.mock('../../modules/auth/usePasswordLogin', () => ({
  usePasswordLogin: () => mocks.login,
}));

vi.mock('../../modules/auth/useEmailCodeLogin', () => ({
  useEmailCodeLogin: () => mocks.emailCodeLogin,
}));

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
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

const user: CurrentUser = {
  id: '1001',
  role: 'USER',
  nickname: '测试用户',
  emailMasked: 'u***@cinewise.test',
  emailVerified: true,
  status: 'NORMAL',
  privacyPolicyVersion: '2026-08-03',
};

const admin: CurrentUser = {
  ...user,
  id: '2001',
  role: 'ADMIN',
  nickname: '测试管理员',
};

function fillValidCredentials() {
  fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
    target: { value: ' user@cinewise.test ' },
  });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'Password1' } });
}

describe('LoginPage', () => {
  beforeEach(() => {
    mocks.auth.currentUser = null;
    mocks.auth.status = 'anonymous';
    mocks.searchParams = new URLSearchParams();
    mocks.login.errorMessage = null;
    mocks.login.clearFeedback.mockReset();
    mocks.login.isSubmitDisabled = false;
    mocks.login.status = 'idle';
    mocks.login.submit.mockReset().mockResolvedValue({ clearPassword: true, user: null });
    mocks.login.retryRecovery.mockReset().mockResolvedValue({
      clearPassword: false,
      user: null,
    });
    mocks.emailCodeLogin.clearFeedback.mockReset();
    mocks.emailCodeLogin.cooldownSeconds = 0;
    mocks.emailCodeLogin.errorMessage = null;
    mocks.emailCodeLogin.isSendCodeDisabled = false;
    mocks.emailCodeLogin.isSubmitDisabled = false;
    mocks.emailCodeLogin.requestCode.mockReset();
    mocks.emailCodeLogin.retryRecovery.mockReset().mockResolvedValue({
      clearCode: false,
      user: null,
    });
    mocks.emailCodeLogin.sendCodeMessage = null;
    mocks.emailCodeLogin.sendCodeStatus = 'idle';
    mocks.emailCodeLogin.status = 'idle';
    mocks.emailCodeLogin.submit.mockReset().mockResolvedValue({ clearCode: true, user: null });
    mocks.navigate.mockReset();
  });

  afterEach(cleanup);

  it('默认显示用户登录文案和注册链接', () => {
    render(<LoginPage />);

    expect(screen.getByRole('heading', { name: '登录' })).toBeInTheDocument();
    expect(screen.getByText('登录后继续购票、查看订单与个性化观影服务')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '立即注册' })).toHaveAttribute('href', '/register');
    expect(screen.getByRole('link', { name: '忘记密码？' })).toHaveAttribute(
      'href',
      '/password/reset',
    );
    expect(screen.getByRole('link', { name: '查看隐私政策' })).toHaveAttribute('href', '/privacy');
    expect(screen.queryByText(/ICP备|公网安备/)).not.toBeInTheDocument();
  });

  it('已登录管理员访问统一登录页时自动进入管理端', () => {
    mocks.auth.currentUser = admin;
    mocks.auth.status = 'authenticated';
    render(<LoginPage />);

    expect(mocks.navigate).toHaveBeenCalledWith('/admin', { replace: true });
  });

  it('邮箱和密码输入具有正确名称并可切换密码可见性', () => {
    render(<LoginPage />);

    expect(screen.getByRole('textbox', { name: '邮箱' })).toHaveAttribute('type', 'email');
    const passwordInput = screen.getByLabelText('密码');
    expect(passwordInput).toHaveAttribute('type', 'password');

    fireEvent.click(screen.getByRole('button', { name: '显示密码' }));
    expect(passwordInput).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: '隐藏密码' })).toBeInTheDocument();
  });

  it('提交前校验邮箱和密码，不调用登录 Hook', () => {
    render(<LoginPage />);

    fireEvent.submit(screen.getByRole('button', { name: /登\s*录/ }).closest('form')!);
    expect(screen.getByRole('alert')).toHaveTextContent('请输入有效邮箱');

    fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
      target: { value: 'user@cinewise.test' },
    });
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'short' } });
    fireEvent.submit(screen.getByRole('button', { name: /登\s*录/ }).closest('form')!);
    expect(screen.getByRole('alert')).toHaveTextContent('密码长度必须为 8 到 128 位');
    expect(mocks.login.submit).not.toHaveBeenCalled();
  });

  it('提交规范化邮箱并按登录用户角色跳转', async () => {
    mocks.login.submit.mockResolvedValue({ clearPassword: true, user });
    mocks.searchParams = new URLSearchParams('returnUrl=%2Fprofile');
    render(<LoginPage />);
    fillValidCredentials();

    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }));

    await waitFor(() =>
      expect(mocks.login.submit).toHaveBeenCalledWith('user@cinewise.test', 'Password1'),
    );
    expect(mocks.navigate).toHaveBeenCalledWith('/profile', { replace: true });
    expect(screen.getByLabelText('密码')).toHaveValue('');
  });

  it('切换到邮箱验证码登录并提交后按安全回跳地址跳转', async () => {
    mocks.searchParams = new URLSearchParams('returnUrl=%2Fprofile');
    mocks.emailCodeLogin.submit.mockResolvedValue({ clearCode: true, user });
    render(<LoginPage />);

    fireEvent.click(screen.getByRole('tab', { name: '验证码登录' }));
    fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
      target: { value: ' user@cinewise.test ' },
    });
    fireEvent.click(screen.getByRole('button', { name: '获取验证码' }));
    fireEvent.change(screen.getByRole('textbox', { name: '邮箱验证码' }), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: '验证码登录' }));

    await waitFor(() =>
      expect(mocks.emailCodeLogin.submit).toHaveBeenCalledWith('user@cinewise.test', '123456'),
    );
    expect(mocks.navigate).toHaveBeenCalledWith('/profile', { replace: true });
    expect(screen.getByRole('textbox', { name: '邮箱验证码' })).toHaveValue('');
  });

  it('验证码登录提交前校验 6 位数字', () => {
    render(<LoginPage />);
    fireEvent.click(screen.getByRole('tab', { name: '验证码登录' }));
    fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
      target: { value: 'user@cinewise.test' },
    });
    fireEvent.submit(screen.getByRole('button', { name: '验证码登录' }).closest('form')!);

    expect(screen.getByRole('alert')).toHaveTextContent('验证码必须为 6 位数字');
    expect(mocks.emailCodeLogin.submit).not.toHaveBeenCalled();
  });

  it('切换登录方式时清空密码和验证码', () => {
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'Password1' } });

    fireEvent.click(screen.getByRole('tab', { name: '验证码登录' }));
    fireEvent.change(screen.getByRole('textbox', { name: '邮箱验证码' }), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('tab', { name: '密码登录' }));

    expect(screen.getByLabelText('密码')).toHaveValue('');
    fireEvent.click(screen.getByRole('tab', { name: '验证码登录' }));
    expect(screen.getByRole('textbox', { name: '邮箱验证码' })).toHaveValue('');
  });

  it('管理员通过同一表单登录后默认进入管理端', async () => {
    mocks.login.submit.mockResolvedValue({ clearPassword: true, user: admin });
    render(<LoginPage />);
    fillValidCredentials();

    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }));

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/admin', { replace: true }));
  });

  it('提交中禁用输入和按钮，防止页面层重复提交', () => {
    mocks.login.status = 'submitting';
    mocks.login.isSubmitDisabled = true;
    render(<LoginPage />);

    expect(screen.getByRole('textbox', { name: '邮箱' })).toBeDisabled();
    expect(screen.getByLabelText('密码')).toBeDisabled();
    expect(screen.getByRole('button', { name: /登\s*录/ })).toBeDisabled();
  });

  it('显示后端错误提示', () => {
    mocks.login.errorMessage = '账号或密码不正确';
    mocks.login.status = 'error';
    render(<LoginPage />);

    expect(screen.getByRole('alert')).toHaveTextContent('账号或密码不正确');
  });

  it('登录结果未知时只提供重新查询入口', async () => {
    mocks.login.errorMessage = '登录结果暂时无法确认，请检查网络后重新查询';
    mocks.login.isSubmitDisabled = true;
    mocks.login.status = 'result-unknown';
    mocks.login.retryRecovery.mockResolvedValue({ clearPassword: true, user });
    render(<LoginPage />);

    fireEvent.click(screen.getByRole('button', { name: '重新查询登录结果' }));

    await waitFor(() => expect(mocks.login.retryRecovery).toHaveBeenCalledOnce());
    expect(mocks.login.submit).not.toHaveBeenCalled();
    expect(mocks.navigate).toHaveBeenCalledWith('/', { replace: true });
  });
});
