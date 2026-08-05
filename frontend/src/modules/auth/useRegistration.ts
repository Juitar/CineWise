import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import { useAuth } from '../../shared/auth/AuthProvider';
import { sendEmailCode, submitRegistration } from './api';
import type { CurrentUser, RegisterRequest } from './types';

export type RegistrationStatus = 'error' | 'idle' | 'recovering' | 'result-unknown' | 'submitting';
export type SendCodeStatus = 'error' | 'idle' | 'sending';

export interface RegistrationFormValues {
  code: string;
  email: string;
  inviteCode: string;
  nickname?: string;
  password: string;
  privacyPolicyVersion: string;
}

interface RegistrationSubmissionResult {
  clearSensitiveInputs: boolean;
  user: CurrentUser | null;
}

let fallbackRequestSequence = 0;

function createRegistrationRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }

  fallbackRequestSequence = (fallbackRequestSequence + 1) % Number.MAX_SAFE_INTEGER;
  const timestamp = Date.now().toString(36);
  const sequence = fallbackRequestSequence.toString(36);
  const randomPart = Math.random().toString(36).slice(2, 14).padEnd(12, '0');

  // HTTP IP 不属于安全上下文，浏览器可能不提供 randomUUID。
  // 该值只用于注册幂等，不能作为密码、Token 或其他安全随机值。
  return `register-${timestamp}-${sequence}-${randomPart}`;
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

function getRegistrationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '注册失败，请稍后重试';
  }

  switch (error.code) {
    case 101001:
      return '注册信息格式不正确，请检查后重试';
    case 201002:
      return '验证码无效或已过期，请重新获取';
    case 201003:
      return '该邮箱已经注册，请直接登录';
    case 201004:
      return '邀请码不可用，请检查后重试';
    case 201008:
      return '请重新确认当前版本隐私政策';
    case 201009:
      return '安全校验已更新，请重新提交注册';
    default:
      return error.kind === 'NETWORK' ? '网络连接失败，请检查网络后重试' : '注册失败，请稍后重试';
  }
}

/**
 * 管理注册验证码冷却、注册提交和结果未知恢复。
 * 注册 POST 结果未知时只查询 `/auth/me`，不会重发密码、验证码或邀请码。
 */
export function useRegistration() {
  const { recoverSession } = useAuth();
  const [cooldownSeconds, setCooldownSeconds] = useState(0);
  const [registrationErrorMessage, setRegistrationErrorMessage] = useState<string | null>(null);
  const [registrationStatus, setRegistrationStatus] = useState<RegistrationStatus>('idle');
  const [sendCodeMessage, setSendCodeMessage] = useState<string | null>(null);
  const [sendCodeStatus, setSendCodeStatus] = useState<SendCodeStatus>('idle');
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
    setRegistrationErrorMessage(null);
    if (sendCodeStatus === 'error') {
      setSendCodeMessage(null);
      setSendCodeStatus('idle');
    }
  }, [sendCodeStatus]);

  const requestCode = useCallback(
    async (email: string): Promise<void> => {
      if (sendInFlight.current || cooldownSeconds > 0) {
        return;
      }

      sendInFlight.current = true;
      setRegistrationErrorMessage(null);
      setSendCodeMessage(null);
      setSendCodeStatus('sending');
      try {
        const response = await sendEmailCode({ email: email.trim(), purpose: 'REGISTER' });
        setCooldownSeconds(Math.max(1, Math.ceil(response.cooldownSeconds)));
        setSendCodeMessage(`验证码已发送，${Math.ceil(response.expiresInSeconds / 60)} 分钟内有效`);
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
    [cooldownSeconds],
  );

  const recover = useCallback(async (): Promise<RegistrationSubmissionResult> => {
    setRegistrationStatus('recovering');
    try {
      const user = await recoverSession();
      if (user) {
        setRegistrationErrorMessage(null);
        setRegistrationStatus('idle');
        return { clearSensitiveInputs: true, user };
      }
      setRegistrationErrorMessage('未建立注册会话，请前往登录页确认账号');
      setRegistrationStatus('error');
      return { clearSensitiveInputs: true, user: null };
    } catch {
      setRegistrationErrorMessage('注册结果暂时无法确认，请检查网络后重新查询');
      setRegistrationStatus('result-unknown');
      return { clearSensitiveInputs: true, user: null };
    }
  }, [recoverSession]);

  const submit = useCallback(
    async (values: RegistrationFormValues): Promise<RegistrationSubmissionResult> => {
      if (submitInFlight.current || registrationStatus === 'result-unknown') {
        return { clearSensitiveInputs: false, user: null };
      }

      submitInFlight.current = true;
      setRegistrationErrorMessage(null);
      setRegistrationStatus('submitting');
      const request: RegisterRequest = {
        clientRequestId: createRegistrationRequestId(),
        code: values.code,
        email: values.email.trim(),
        inviteCode: values.inviteCode,
        nickname: values.nickname?.trim() || undefined,
        password: values.password,
        privacyAccepted: true,
        privacyPolicyVersion: values.privacyPolicyVersion,
      };

      try {
        await submitRegistration(request);
        return await recover();
      } catch (error) {
        if (error instanceof ApiError && error.isResultUnknown) {
          return recover();
        }
        setRegistrationErrorMessage(getRegistrationErrorMessage(error));
        setRegistrationStatus('error');
        return { clearSensitiveInputs: true, user: null };
      } finally {
        submitInFlight.current = false;
      }
    },
    [recover, registrationStatus],
  );

  const retryRecovery = useCallback(async (): Promise<RegistrationSubmissionResult> => {
    if (submitInFlight.current) {
      return { clearSensitiveInputs: false, user: null };
    }
    submitInFlight.current = true;
    try {
      return await recover();
    } finally {
      submitInFlight.current = false;
    }
  }, [recover]);

  return {
    clearFeedback,
    cooldownSeconds,
    isSendCodeDisabled:
      cooldownSeconds > 0 || sendCodeStatus === 'sending' || registrationStatus === 'submitting',
    isSubmitDisabled:
      registrationStatus === 'recovering' ||
      registrationStatus === 'result-unknown' ||
      registrationStatus === 'submitting',
    registrationErrorMessage,
    registrationStatus,
    requestCode,
    retryRecovery,
    sendCodeMessage,
    sendCodeStatus,
    submit,
  };
}
