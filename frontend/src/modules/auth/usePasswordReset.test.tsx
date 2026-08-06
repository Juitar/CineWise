import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import { usePasswordReset } from './usePasswordReset';

const apiMocks = vi.hoisted(() => ({
  sendEmailCode: vi.fn(),
  submitPasswordReset: vi.fn(),
}));

vi.mock('./api', () => apiMocks);

describe('usePasswordReset', () => {
  beforeEach(() => {
    apiMocks.sendEmailCode.mockReset();
    apiMocks.submitPasswordReset.mockReset();
    vi.stubGlobal('crypto', { randomUUID: () => 'password-reset-uuid' });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('发送 RESET_PASSWORD 验证码并使用服务端冷却时间', async () => {
    vi.useFakeTimers();
    apiMocks.sendEmailCode.mockResolvedValue({ cooldownSeconds: 60, expiresInSeconds: 300 });
    const { result } = renderHook(() => usePasswordReset());

    await act(async () => {
      await result.current.requestCode(' user@cinewise.test ');
    });

    expect(apiMocks.sendEmailCode).toHaveBeenCalledWith({
      email: 'user@cinewise.test',
      purpose: 'RESET_PASSWORD',
    });
    expect(result.current.cooldownSeconds).toBe(60);
    expect(result.current.isSendCodeDisabled).toBe(true);
  });

  it('提交单次 clientRequestId 并返回成功', async () => {
    apiMocks.submitPasswordReset.mockResolvedValue({ changed: true });
    const { result } = renderHook(() => usePasswordReset());

    let submission;
    await act(async () => {
      submission = await result.current.submit(' user@cinewise.test ', '123456', 'NewPassword1');
    });

    expect(apiMocks.submitPasswordReset).toHaveBeenCalledWith({
      clientRequestId: 'password-reset-uuid',
      code: '123456',
      email: 'user@cinewise.test',
      newPassword: 'NewPassword1',
    });
    expect(submission).toEqual({ changed: true, clearSensitiveInputs: true });
  });

  it('验证码无效时显示安全提示并清理敏感输入', async () => {
    apiMocks.submitPasswordReset.mockRejectedValue(
      new ApiError('invalid', { code: 201002, kind: 'HTTP', status: 422 }),
    );
    const { result } = renderHook(() => usePasswordReset());

    let submission;
    await act(async () => {
      submission = await result.current.submit('user@cinewise.test', '123456', 'NewPassword1');
    });

    expect(result.current.errorMessage).toBe('验证码无效或已过期，请重新获取');
    expect(result.current.status).toBe('error');
    expect(submission).toEqual({ changed: false, clearSensitiveInputs: true });
  });

  it.each([
    [401, '当前请求未通过认证校验，请重新获取验证码'],
    [403, '安全校验已失效，请重新获取验证码'],
    [500, '密码重置失败，请稍后重试'],
  ])('处理 HTTP %s 且不自动重发', async (status, message) => {
    apiMocks.submitPasswordReset.mockRejectedValue(
      new ApiError('failed', { kind: 'HTTP', status }),
    );
    const { result } = renderHook(() => usePasswordReset());

    await act(async () => {
      await result.current.submit('user@cinewise.test', '123456', 'NewPassword1');
    });

    expect(result.current.errorMessage).toBe(message);
    expect(apiMocks.submitPasswordReset).toHaveBeenCalledOnce();
  });

  it('发送 429 时展示频率提示', async () => {
    apiMocks.sendEmailCode.mockRejectedValue(
      new ApiError('limited', { code: 101002, kind: 'HTTP', status: 429 }),
    );
    const { result } = renderHook(() => usePasswordReset());

    await act(async () => {
      await result.current.requestCode('user@cinewise.test');
    });

    expect(result.current.sendCodeMessage).toBe('发送过于频繁，请稍后再试');
    expect(apiMocks.sendEmailCode).toHaveBeenCalledOnce();
  });

  it('网络结果未知时禁用重复提交并要求用户主动重新开始', async () => {
    apiMocks.submitPasswordReset.mockRejectedValue(
      new ApiError('timeout', { isResultUnknown: true, kind: 'TIMEOUT' }),
    );
    const { result } = renderHook(() => usePasswordReset());

    let first;
    await act(async () => {
      first = await result.current.submit('user@cinewise.test', '123456', 'NewPassword1');
    });
    await act(async () => {
      await result.current.submit('user@cinewise.test', '123456', 'NewPassword1');
    });

    expect(first).toEqual({ changed: false, clearSensitiveInputs: true });
    expect(apiMocks.submitPasswordReset).toHaveBeenCalledOnce();
    expect(result.current.status).toBe('result-unknown');
    expect(result.current.isSubmitDisabled).toBe(true);

    act(() => result.current.startOver());
    expect(result.current.status).toBe('idle');
  });
});
