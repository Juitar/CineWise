import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type {
  CurrentUser,
  EmailCodeLoginRequest,
  PasswordResetRequest,
  RegisterRequest,
} from './types';
import {
  sendEmailCode,
  submitEmailCodeLogin,
  submitPasswordReset,
  submitRegistration,
} from './api';

const clientMocks = vi.hoisted(() => ({
  apiRequest: vi.fn(),
  clearCsrfToken: vi.fn(),
}));

vi.mock('../../shared/api/client', () => clientMocks);

const user: CurrentUser = {
  id: '1001',
  role: 'USER',
  nickname: '测试用户',
  emailMasked: 'u***@cinewise.test',
  emailVerified: true,
  status: 'NORMAL',
  privacyPolicyVersion: '2026-08-03',
};

const registrationRequest: RegisterRequest = {
  clientRequestId: 'registration-1',
  code: '123456',
  email: 'user@cinewise.test',
  inviteCode: 'private-invite',
  password: 'Password1',
  privacyAccepted: true,
  privacyPolicyVersion: '2026-08-03',
};

const emailCodeLoginRequest: EmailCodeLoginRequest = {
  clientRequestId: 'email-login-1',
  code: '123456',
  email: 'user@cinewise.test',
};

const passwordResetRequest: PasswordResetRequest = {
  clientRequestId: 'password-reset-1',
  code: '123456',
  email: 'user@cinewise.test',
  newPassword: 'NewPassword1',
};

describe('auth api', () => {
  beforeEach(() => {
    clientMocks.apiRequest.mockReset();
    clientMocks.clearCsrfToken.mockReset();
  });

  it('使用 REGISTER 用途申请邮箱验证码', async () => {
    clientMocks.apiRequest.mockResolvedValue({ cooldownSeconds: 60, expiresInSeconds: 300 });

    await sendEmailCode({ email: 'user@cinewise.test', purpose: 'REGISTER' });

    expect(clientMocks.apiRequest).toHaveBeenCalledWith('/api/v1/auth/email-codes', {
      body: { email: 'user@cinewise.test', purpose: 'REGISTER' },
      method: 'POST',
    });
  });

  it('使用 RESET_PASSWORD 用途发送验证码并提交重置接口', async () => {
    clientMocks.apiRequest
      .mockResolvedValueOnce({ cooldownSeconds: 60, expiresInSeconds: 300 })
      .mockResolvedValueOnce({ changed: true });

    await sendEmailCode({ email: 'user@cinewise.test', purpose: 'RESET_PASSWORD' });
    await expect(submitPasswordReset(passwordResetRequest)).resolves.toEqual({ changed: true });

    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(1, '/api/v1/auth/email-codes', {
      body: { email: 'user@cinewise.test', purpose: 'RESET_PASSWORD' },
      method: 'POST',
    });
    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(2, '/api/v1/auth/password/reset', {
      body: passwordResetRequest,
      method: 'POST',
    });
    expect(clientMocks.clearCsrfToken).not.toHaveBeenCalled();
  });

  it('提交注册成功后清除匿名阶段 CSRF Token', async () => {
    clientMocks.apiRequest.mockResolvedValue(user);

    await expect(submitRegistration(registrationRequest)).resolves.toEqual(user);

    expect(clientMocks.apiRequest).toHaveBeenCalledWith('/api/v1/auth/register', {
      body: registrationRequest,
      method: 'POST',
    });
    expect(clientMocks.clearCsrfToken).toHaveBeenCalledOnce();
  });

  it('提交邮箱验证码登录成功后调用正确接口并清除匿名阶段 CSRF Token', async () => {
    clientMocks.apiRequest.mockResolvedValue(user);

    await expect(submitEmailCodeLogin(emailCodeLoginRequest)).resolves.toEqual(user);

    expect(clientMocks.apiRequest).toHaveBeenCalledWith('/api/v1/auth/login/email', {
      body: emailCodeLoginRequest,
      method: 'POST',
    });
    expect(clientMocks.clearCsrfToken).toHaveBeenCalledOnce();
  });

  it('邮箱验证码登录结果未知时清除旧 CSRF Token 并保留结构化错误', async () => {
    const error = new ApiError('timeout', { kind: 'TIMEOUT', isResultUnknown: true });
    clientMocks.apiRequest.mockRejectedValue(error);

    await expect(submitEmailCodeLogin(emailCodeLoginRequest)).rejects.toBe(error);
    expect(clientMocks.clearCsrfToken).toHaveBeenCalledOnce();
  });

  it('注册结果未知时清除旧 CSRF Token 并保留结构化错误', async () => {
    const error = new ApiError('timeout', { kind: 'TIMEOUT', isResultUnknown: true });
    clientMocks.apiRequest.mockRejectedValue(error);

    await expect(submitRegistration(registrationRequest)).rejects.toBe(error);
    expect(clientMocks.clearCsrfToken).toHaveBeenCalledOnce();
  });
});
