import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import { useAuth } from '../../shared/auth/AuthProvider';
import { sendEmailCode } from './api';
import type { CurrentUser } from './types';

export type EmailCodeLoginStatus =
  'error' | 'idle' | 'recovering' | 'result-unknown' | 'submitting';
export type LoginCodeSendStatus = 'error' | 'idle' | 'sending';

interface EmailCodeLoginResult {
  clearCode: boolean;
  user: CurrentUser | null;
}

let fallbackRequestSequence = 0;

function createEmailCodeLoginRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }

  fallbackRequestSequence = (fallbackRequestSequence + 1) % Number.MAX_SAFE_INTEGER;
  const timestamp = Date.now().toString(36);
  const sequence = fallbackRequestSequence.toString(36);
  const randomPart = Math.random().toString(36).slice(2, 14).padEnd(12, '0');
  return `email-login-${timestamp}-${sequence}-${randomPart}`;
}

function getSendCodeErrorMessage(error: unknown): string {
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

function getLoginErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '登录失败，请稍后重试';
  }

  switch (error.code) {
    case 101001:
      return '登录信息格式不正确，请检查后重试';
    case 201002:
      return '验证码无效或已过期，请重新获取';
    case 201005:
      return '账号当前不可用，请联系管理员';
    case 201009:
      return '安全校验已更新，请重新提交登录';
    default:
      return error.kind === 'NETWORK' ? '网络连接失败，请检查网络后重试' : '登录失败，请稍后重试';
  }
}

/** 管理 LOGIN 验证码发送、一次性登录和结果未知恢复，不自动重发写请求。 */
export function useEmailCodeLogin() {
  const { loginWithEmailCode, recoverSession } = useAuth();
  const [cooldownSeconds, setCooldownSeconds] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [sendCodeMessage, setSendCodeMessage] = useState<string | null>(null);
  const [sendCodeStatus, setSendCodeStatus] = useState<LoginCodeSendStatus>('idle');
  const [status, setStatus] = useState<EmailCodeLoginStatus>('idle');
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
        const response = await sendEmailCode({ email: email.trim(), purpose: 'LOGIN' });
        setCooldownSeconds(Math.max(1, Math.ceil(response.cooldownSeconds)));
        setSendCodeMessage('请检查邮箱');
        setSendCodeStatus('idle');
      } catch (error) {
        if (error instanceof ApiError && error.isResultUnknown) {
          setCooldownSeconds(60);
          setSendCodeMessage('发送结果暂时无法确认，请先检查邮箱，倒计时结束后可重新获取');
        } else {
          setSendCodeMessage(getSendCodeErrorMessage(error));
        }
        setSendCodeStatus('error');
      } finally {
        sendInFlight.current = false;
      }
    },
    [cooldownSeconds, status],
  );

  const recover = useCallback(async (): Promise<EmailCodeLoginResult> => {
    setStatus('recovering');
    try {
      const user = await recoverSession();
      if (user) {
        setErrorMessage(null);
        setStatus('idle');
        return { clearCode: true, user };
      }
      setErrorMessage('未建立登录会话，请重新获取验证码');
      setStatus('error');
      return { clearCode: true, user: null };
    } catch {
      setErrorMessage('登录结果暂时无法确认，请检查网络后重新查询');
      setStatus('result-unknown');
      return { clearCode: true, user: null };
    }
  }, [recoverSession]);

  const submit = useCallback(
    async (email: string, code: string): Promise<EmailCodeLoginResult> => {
      if (submitInFlight.current || status === 'result-unknown') {
        return { clearCode: false, user: null };
      }

      submitInFlight.current = true;
      setErrorMessage(null);
      setStatus('submitting');
      try {
        const user = await loginWithEmailCode({
          clientRequestId: createEmailCodeLoginRequestId(),
          code,
          email: email.trim(),
        });
        setStatus('idle');
        return { clearCode: true, user };
      } catch (error) {
        if (error instanceof ApiError && error.isResultUnknown) {
          return recover();
        }
        setErrorMessage(getLoginErrorMessage(error));
        setStatus('error');
        return { clearCode: true, user: null };
      } finally {
        submitInFlight.current = false;
      }
    },
    [loginWithEmailCode, recover, status],
  );

  const retryRecovery = useCallback(async (): Promise<EmailCodeLoginResult> => {
    if (submitInFlight.current) {
      return { clearCode: false, user: null };
    }
    submitInFlight.current = true;
    try {
      return await recover();
    } finally {
      submitInFlight.current = false;
    }
  }, [recover]);

  const loginBusy =
    status === 'recovering' || status === 'result-unknown' || status === 'submitting';

  return {
    clearFeedback,
    cooldownSeconds,
    errorMessage,
    isSendCodeDisabled: cooldownSeconds > 0 || sendCodeStatus === 'sending' || loginBusy,
    isSubmitDisabled: loginBusy,
    requestCode,
    retryRecovery,
    sendCodeMessage,
    sendCodeStatus,
    status,
    submit,
  };
}
