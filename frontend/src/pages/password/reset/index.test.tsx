import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import PasswordResetPage from './index';

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  reset: {
    clearFeedback: vi.fn(),
    cooldownSeconds: 0,
    errorMessage: null as string | null,
    isSendCodeDisabled: false,
    isSubmitDisabled: false,
    requestCode: vi.fn(),
    sendCodeMessage: null as string | null,
    sendCodeStatus: 'idle' as 'error' | 'idle' | 'sending',
    startOver: vi.fn(),
    status: 'idle' as 'error' | 'idle' | 'result-unknown' | 'submitting',
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

vi.mock('../../../modules/auth/usePasswordReset', () => ({
  usePasswordReset: () => mocks.reset,
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

function fillForm(confirmPassword = 'NewPassword1') {
  fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
    target: { value: ' user@cinewise.test ' },
  });
  fireEvent.change(screen.getByRole('textbox', { name: '邮箱验证码' }), {
    target: { value: '123456' },
  });
  fireEvent.change(screen.getByLabelText('新密码'), { target: { value: 'NewPassword1' } });
  fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: confirmPassword } });
}

describe('PasswordResetPage', () => {
  beforeEach(() => {
    mocks.navigate.mockReset();
    mocks.reset.clearFeedback.mockReset();
    mocks.reset.cooldownSeconds = 0;
    mocks.reset.errorMessage = null;
    mocks.reset.isSendCodeDisabled = false;
    mocks.reset.isSubmitDisabled = false;
    mocks.reset.requestCode.mockReset().mockResolvedValue(undefined);
    mocks.reset.sendCodeMessage = null;
    mocks.reset.sendCodeStatus = 'idle';
    mocks.reset.startOver.mockReset();
    mocks.reset.status = 'idle';
    mocks.reset.submit.mockReset().mockResolvedValue({
      changed: false,
      clearSensitiveInputs: true,
    });
  });

  afterEach(cleanup);

  it('显示重置字段、登录入口和移动端可用控件', () => {
    render(<PasswordResetPage />);

    expect(screen.getByRole('heading', { name: '重置密码' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '邮箱' })).toHaveAttribute('maxlength', '255');
    expect(screen.getByRole('textbox', { name: '邮箱验证码' })).toHaveAttribute('maxlength', '6');
    expect(screen.getByLabelText('新密码')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByLabelText('确认新密码')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByRole('link', { name: '返回登录' })).toHaveAttribute('href', '/login');
  });

  it('邮箱合法后请求 RESET_PASSWORD 验证码', async () => {
    render(<PasswordResetPage />);
    fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
      target: { value: ' user@cinewise.test ' },
    });
    fireEvent.click(screen.getByRole('button', { name: '获取验证码' }));

    await waitFor(() => expect(mocks.reset.requestCode).toHaveBeenCalledWith('user@cinewise.test'));
  });

  it('客户端拒绝弱密码和两次密码不一致', () => {
    render(<PasswordResetPage />);
    fillForm('DifferentPassword1');
    fireEvent.click(screen.getByRole('button', { name: '确认重置' }));
    expect(screen.getByRole('alert')).toHaveTextContent('两次输入的密码不一致');
    expect(mocks.reset.submit).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: 'password' } });
    fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: 'password' } });
    fireEvent.click(screen.getByRole('button', { name: '确认重置' }));
    expect(screen.getByRole('alert')).toHaveTextContent('同时包含字母和数字');
  });

  it('成功后清理密码和验证码并返回登录页', async () => {
    mocks.reset.submit.mockResolvedValue({ changed: true, clearSensitiveInputs: true });
    render(<PasswordResetPage />);
    fillForm();
    fireEvent.click(screen.getByRole('button', { name: '确认重置' }));

    await waitFor(() =>
      expect(mocks.reset.submit).toHaveBeenCalledWith(
        'user@cinewise.test',
        '123456',
        'NewPassword1',
      ),
    );
    expect(screen.getByRole('textbox', { name: '邮箱验证码' })).toHaveValue('');
    expect(screen.getByLabelText('新密码')).toHaveValue('');
    expect(screen.getByLabelText('确认新密码')).toHaveValue('');
    expect(mocks.navigate).toHaveBeenCalledWith('/login', { replace: true });
  });

  it('结果未知时不自动提交并提供登录或重新开始', () => {
    mocks.reset.errorMessage = '重置结果暂时无法确认，请用新密码尝试登录，不要自动重复提交';
    mocks.reset.isSubmitDisabled = true;
    mocks.reset.status = 'result-unknown';
    render(<PasswordResetPage />);

    expect(screen.getByRole('alert')).toHaveTextContent('不要自动重复提交');
    expect(screen.getByRole('button', { name: '确认重置' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '用新密码尝试登录' }));
    expect(mocks.navigate).toHaveBeenCalledWith('/login');
    fireEvent.click(screen.getByRole('button', { name: '重新开始' }));
    expect(mocks.reset.startOver).toHaveBeenCalledOnce();
    expect(mocks.reset.submit).not.toHaveBeenCalled();
  });

  it('不把密码和验证码写入 URL 或浏览器存储', async () => {
    const localSpy = vi.spyOn(Storage.prototype, 'setItem');
    render(<PasswordResetPage />);
    fillForm();
    fireEvent.click(screen.getByRole('button', { name: '确认重置' }));
    await waitFor(() => expect(mocks.reset.submit).toHaveBeenCalledOnce());

    expect(window.location.href).not.toContain('123456');
    expect(window.location.href).not.toContain('NewPassword1');
    expect(localSpy).not.toHaveBeenCalledWith(expect.anything(), expect.stringContaining('123456'));
    expect(localSpy).not.toHaveBeenCalledWith(
      expect.anything(),
      expect.stringContaining('NewPassword1'),
    );
    localSpy.mockRestore();
  });
});
