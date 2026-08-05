import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { CurrentUser, RegisterRequest } from './types';
import { sendEmailCode, submitRegistration } from './api';

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

  it('提交注册成功后清除匿名阶段 CSRF Token', async () => {
    clientMocks.apiRequest.mockResolvedValue(user);

    await expect(submitRegistration(registrationRequest)).resolves.toEqual(user);

    expect(clientMocks.apiRequest).toHaveBeenCalledWith('/api/v1/auth/register', {
      body: registrationRequest,
      method: 'POST',
    });
    expect(clientMocks.clearCsrfToken).toHaveBeenCalledOnce();
  });

  it('注册结果未知时清除旧 CSRF Token 并保留结构化错误', async () => {
    const error = new ApiError('timeout', { kind: 'TIMEOUT', isResultUnknown: true });
    clientMocks.apiRequest.mockRejectedValue(error);

    await expect(submitRegistration(registrationRequest)).rejects.toBe(error);
    expect(clientMocks.clearCsrfToken).toHaveBeenCalledOnce();
  });
});
