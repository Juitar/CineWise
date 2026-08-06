import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import { sendEmailCode, submitPasswordReset } from './api';

export type PasswordResetStatus = 'error' | 'idle' | 'result-unknown' | 'submitting';
export type PasswordResetCodeStatus = 'error' | 'idle' | 'sending';

interface PasswordResetResult {
  changed: boolean;
  clearSensitiveInputs: boolean;
}

let fallbackRequestSequence = 0;

function createPasswordResetRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  fallbackRequestSequence = (fallbackRequestSequence + 1) % Number.MAX_SAFE_INTEGER;
  const timestamp = Date.now().toString(36);
  const sequence = fallbackRequestSequence.toString(36);
  const randomPart = Math.random().toString(36).slice(2, 14).padEnd(12, '0');
  return `password-reset-${timestamp}-${sequence}-${randomPart}`;
}

function sendCodeErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '验证码发送失败，请稍后重试';
  }
  switch (error.code) {
    case 101001:
      return '请输入有效邮箱';
    case 101002:
      return '发送过于频繁，请稍后再试';
    case 201009:
      return '安全校验已更新，请重新获取验证码';
    case 301001:
      return '邮件服务暂不可用，请稍后重试';
    default:
      return error.kind === 'NETWORK'
        ? '网络连接失败，请检查网络后重试'
        : '验证码发送失败，请稍后重试';
  }
}

function resetErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '密码重置失败，请稍后重试';
  }
  switch (error.code) {
    case 101001:
      return '重置信息格式不正确，请检查后重试';
    case 201002:
      return '验证码无效或已过期，请重新获取';
    case 201009:
      return '安全校验已更新，请重新提交';
    default:
      if (error.status === 401) {
        return '当前请求未通过认证校验，请重新获取验证码';
      }
      if (error.status === 403) {
        return '安全校验已失效，请重新获取验证码';
      }
      return error.kind === 'NETWORK'
        ? '网络连接失败，重置结果无法确认，请用新密码尝试登录'
        : '密码重置失败，请稍后重试';
  }
}

/** 管理 RESET_PASSWORD 验证码、冷却和一次性重置；任何未知结果都不自动重发。 */
export function usePasswordReset() {
  const [cooldownSeconds, setCooldownSeconds] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [sendCodeMessage, setSendCodeMessage] = useState<string | null>(null);
  const [sendCodeStatus, setSendCodeStatus] = useState<PasswordResetCodeStatus>('idle');
  const [status, setStatus] = useState<PasswordResetStatus>('idle');
  const sendInFlight = useRef(false);
  const submitInFlight = useRef(false);

  useEffect(() => {
    if (cooldownSeconds <= 0) {
      return undefined;
    }
    const timeoutId = window.setTimeout(() => {
      setCooldownSeconds((current) => Math.max(0, current - 1));
    }, 1000);
    return () => window.clearTimeout(timeoutId);
  }, [cooldownSeconds]);

  const clearFeedback = useCallback(() => {
    setErrorMessage(null);
    setStatus((current) => (current === 'error' ? 'idle' : current));
    if (sendCodeStatus === 'error') {
      setSendCodeMessage(null);
      setSendCodeStatus('idle');
    }
  }, [sendCodeStatus]);

  const requestCode = useCallback(
    async (email: string): Promise<void> => {
      if (sendInFlight.current || cooldownSeconds > 0 || status === 'result-unknown') {
        return;
      }
      sendInFlight.current = true;
      setErrorMessage(null);
      setSendCodeMessage(null);
      setSendCodeStatus('sending');
      try {
        const response = await sendEmailCode({ email: email.trim(), purpose: 'RESET_PASSWORD' });
        setCooldownSeconds(Math.max(1, Math.ceil(response.cooldownSeconds)));
        setSendCodeMessage(`验证码已发送，${Math.ceil(response.expiresInSeconds / 60)} 分钟内有效`);
        setSendCodeStatus('idle');
      } catch (error) {
        if (error instanceof ApiError && error.isResultUnknown) {
          setCooldownSeconds(60);
          setSendCodeMessage('发送结果暂时无法确认，请先检查邮箱，倒计时结束后可重新获取');
        } else {
          setSendCodeMessage(sendCodeErrorMessage(error));
        }
        setSendCodeStatus('error');
      } finally {
        sendInFlight.current = false;
      }
    },
    [cooldownSeconds, status],
  );

  const submit = useCallback(
    async (email: string, code: string, newPassword: string): Promise<PasswordResetResult> => {
      if (submitInFlight.current || status === 'result-unknown') {
        return { changed: false, clearSensitiveInputs: false };
      }
      submitInFlight.current = true;
      setErrorMessage(null);
      setStatus('submitting');
      try {
        const response = await submitPasswordReset({
          clientRequestId: createPasswordResetRequestId(),
          code,
          email: email.trim(),
          newPassword,
        });
        setStatus('idle');
        return { changed: response.changed, clearSensitiveInputs: true };
      } catch (error) {
        if (error instanceof ApiError && error.isResultUnknown) {
          setErrorMessage('重置结果暂时无法确认，请用新密码尝试登录，不要自动重复提交');
          setStatus('result-unknown');
        } else {
          setErrorMessage(resetErrorMessage(error));
          setStatus('error');
        }
        return { changed: false, clearSensitiveInputs: true };
      } finally {
        submitInFlight.current = false;
      }
    },
    [status],
  );

  const startOver = useCallback(() => {
    setErrorMessage(null);
    setSendCodeMessage(null);
    setStatus('idle');
  }, []);

  const busy = status === 'result-unknown' || status === 'submitting';
  return {
    clearFeedback,
    cooldownSeconds,
    errorMessage,
    isSendCodeDisabled: cooldownSeconds > 0 || sendCodeStatus === 'sending' || busy,
    isSubmitDisabled: busy,
    requestCode,
    sendCodeMessage,
    sendCodeStatus,
    startOver,
    status,
    submit,
  };
}
