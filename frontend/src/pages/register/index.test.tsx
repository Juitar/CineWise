import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CurrentUser } from '../../modules/auth/types';
import RegisterPage from './index';

const mocks = vi.hoisted(() => ({
  auth: {
    currentUser: null as CurrentUser | null,
    status: 'anonymous',
  },
  navigate: vi.fn(),
  isMobile: false,
  registration: {
    clearFeedback: vi.fn(),
    cooldownSeconds: 0,
    isSendCodeDisabled: false,
    isSubmitDisabled: false,
    registrationErrorMessage: null as string | null,
    registrationStatus: 'idle',
    requestCode: vi.fn(),
    retryRecovery: vi.fn(),
    sendCodeMessage: null as string | null,
    sendCodeStatus: 'idle',
    submit: vi.fn(),
  },
}));

vi.mock('umi', () => ({
  Link: ({ children, to, ...props }: PropsWithChildren<{ to: string }>) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useNavigate: () => mocks.navigate,
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => mocks.auth,
}));

vi.mock('../../modules/auth/useRegistration', () => ({
  useRegistration: () => mocks.registration,
}));

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: mocks.isMobile,
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

function fillValidRegistration() {
  fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
    target: { value: ' user@cinewise.test ' },
  });
  fireEvent.change(screen.getByRole('textbox', { name: '邮箱验证码' }), {
    target: { value: '123456' },
  });
  fireEvent.change(screen.getByRole('textbox', { name: '邀请码' }), {
    target: { value: 'private-invite' },
  });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'Password1' } });
  fireEvent.change(screen.getByLabelText('确认密码'), { target: { value: 'Password1' } });
  fireEvent.click(screen.getByRole('checkbox'));
}

describe('RegisterPage', () => {
  beforeEach(() => {
    mocks.auth.currentUser = null;
    mocks.auth.status = 'anonymous';
    mocks.navigate.mockReset();
    mocks.isMobile = false;
    mocks.registration.clearFeedback.mockReset();
    mocks.registration.cooldownSeconds = 0;
    mocks.registration.isSendCodeDisabled = false;
    mocks.registration.isSubmitDisabled = false;
    mocks.registration.registrationErrorMessage = null;
    mocks.registration.registrationStatus = 'idle';
    mocks.registration.requestCode.mockReset().mockResolvedValue(undefined);
    mocks.registration.retryRecovery.mockReset().mockResolvedValue({
      clearSensitiveInputs: true,
      user: null,
    });
    mocks.registration.sendCodeMessage = null;
    mocks.registration.sendCodeStatus = 'idle';
    mocks.registration.submit.mockReset().mockResolvedValue({
      clearSensitiveInputs: true,
      user: null,
    });
  });

  afterEach(cleanup);

  it('显示完整注册字段且隐私政策默认未勾选', () => {
    render(<RegisterPage />);

    expect(screen.getByRole('heading', { name: '注册' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '邮箱' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '邮箱验证码' })).toHaveAttribute('maxlength', '6');
    expect(screen.getByRole('textbox', { name: '邀请码' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '昵称（选填）' })).toBeInTheDocument();
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(screen.queryByText(/暂未开放|静态页面预览/)).not.toBeInTheDocument();
  });

  it('移动端显示返回首页入口', async () => {
    mocks.isMobile = true;
    render(<RegisterPage />);

    await waitFor(() =>
      expect(document.querySelector('.register-page-container')).toHaveClass('login-page--mobile'),
    );
    expect(screen.getByRole('link', { name: '返回首页' })).toHaveAttribute('href', '/');
  });

  it('邮箱合法后发送 REGISTER 验证码', async () => {
    render(<RegisterPage />);
    const sendButton = screen.getByRole('button', { name: '获取验证码' });
    expect(sendButton).toBeDisabled();

    fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
      target: { value: ' user@cinewise.test ' },
    });
    fireEvent.click(sendButton);

    await waitFor(() =>
      expect(mocks.registration.requestCode).toHaveBeenCalledWith('user@cinewise.test'),
    );
  });

  it('提交前校验验证码、密码和隐私同意', () => {
    render(<RegisterPage />);
    fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
      target: { value: 'user@cinewise.test' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^注\s*册$/ }));

    expect(screen.getByRole('alert')).toHaveTextContent('验证码必须为 6 位数字');
    expect(mocks.registration.submit).not.toHaveBeenCalled();
  });

  it('提交规范化注册字段并在恢复会话后进入首页', async () => {
    mocks.registration.submit.mockResolvedValue({ clearSensitiveInputs: true, user });
    render(<RegisterPage />);
    fillValidRegistration();

    fireEvent.click(screen.getByRole('button', { name: /^注\s*册$/ }));

    await waitFor(() =>
      expect(mocks.registration.submit).toHaveBeenCalledWith({
        code: '123456',
        email: 'user@cinewise.test',
        inviteCode: 'private-invite',
        nickname: undefined,
        password: 'Password1',
        privacyPolicyVersion: '2026-08-03',
      }),
    );
    expect(mocks.navigate).toHaveBeenCalledWith('/', { replace: true });
    expect(screen.getByLabelText('密码')).toHaveValue('');
    expect(screen.getByRole('textbox', { name: '邀请码' })).toHaveValue('');
  });

  it('展示后端业务错误和结果未知恢复入口', () => {
    mocks.registration.registrationErrorMessage = '邀请码不可用，请检查后重试';
    mocks.registration.registrationStatus = 'result-unknown';
    mocks.registration.isSubmitDisabled = true;
    render(<RegisterPage />);

    expect(screen.getByRole('alert')).toHaveTextContent('邀请码不可用，请检查后重试');
    expect(screen.getByRole('button', { name: '重新查询注册结果' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^注\s*册$/ })).toBeDisabled();
  });
});
