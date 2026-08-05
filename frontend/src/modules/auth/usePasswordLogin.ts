import { useCallback, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import { useAuth } from '../../shared/auth/AuthProvider';
import type { CurrentUser } from './types';

export type LoginSubmissionStatus =
  'error' | 'idle' | 'recovering' | 'result-unknown' | 'submitting';

interface LoginSubmissionResult {
  clearPassword: boolean;
  user: CurrentUser | null;
}

let fallbackRequestSequence = 0;

function createClientRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }

  fallbackRequestSequence = (fallbackRequestSequence + 1) % Number.MAX_SAFE_INTEGER;
  const timestamp = Date.now().toString(36);
  const sequence = fallbackRequestSequence.toString(36);
  const randomPart = Math.random().toString(36).slice(2, 14).padEnd(12, '0');

  // HTTP IP 不属于安全上下文，浏览器可能不提供 randomUUID。
  // 该值只用于登录请求追踪和幂等，不可作为密码、Token 或其他安全随机值。
  return `login-${timestamp}-${sequence}-${randomPart}`;
}

function getLoginErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '登录失败，请稍后重试';
  }

  switch (error.code) {
    case 101001:
      return '请检查邮箱和密码格式';
    case 201001:
      return '账号或密码不正确';
    case 201005:
      return '账号不可用，请联系管理员';
    case 201007:
      return '当前账号没有管理权限';
    case 201009:
      return '安全校验已更新，请重新点击登录';
    default:
      return error.kind === 'NETWORK' ? '网络连接失败，请检查网络后重试' : '登录失败，请稍后重试';
  }
}

/**
 * 管理一次密码登录提交及结果未知恢复。
 * `result-unknown` 状态只允许查询 `/auth/me`，不会重新发送密码。
 */
export function usePasswordLogin() {
  const { login, recoverSession } = useAuth();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [status, setStatus] = useState<LoginSubmissionStatus>('idle');
  const inFlight = useRef(false);

  const clearFeedback = useCallback(() => {
    setErrorMessage(null);
    setStatus((current) => (current === 'error' ? 'idle' : current));
  }, []);

  const recover = useCallback(async (): Promise<LoginSubmissionResult> => {
    setStatus('recovering');
    try {
      const user = await recoverSession();
      if (user) {
        setErrorMessage(null);
        setStatus('idle');
        return { clearPassword: true, user };
      }
      setErrorMessage('未建立登录会话，请重新输入密码后提交');
      setStatus('error');
      return { clearPassword: true, user: null };
    } catch {
      setErrorMessage('登录结果暂时无法确认，请检查网络后重新查询');
      setStatus('result-unknown');
      return { clearPassword: false, user: null };
    }
  }, [recoverSession]);

  const submit = useCallback(
    async (email: string, password: string): Promise<LoginSubmissionResult> => {
      if (inFlight.current || status === 'result-unknown') {
        return { clearPassword: false, user: null };
      }

      inFlight.current = true;
      setErrorMessage(null);
      setStatus('submitting');
      try {
        const user = await login({
          clientRequestId: createClientRequestId(),
          email: email.trim(),
          password,
        });
        setStatus('idle');
        return { clearPassword: true, user };
      } catch (error) {
        if (error instanceof ApiError && error.isResultUnknown) {
          return recover();
        }
        setErrorMessage(getLoginErrorMessage(error));
        setStatus('error');
        return { clearPassword: true, user: null };
      } finally {
        inFlight.current = false;
      }
    },
    [login, recover, status],
  );

  const retryRecovery = useCallback(async () => {
    if (inFlight.current) {
      return { clearPassword: false, user: null };
    }
    inFlight.current = true;
    try {
      return await recover();
    } finally {
      inFlight.current = false;
    }
  }, [recover]);

  return {
    clearFeedback,
    errorMessage,
    isSubmitDisabled:
      status === 'recovering' || status === 'result-unknown' || status === 'submitting',
    retryRecovery,
    status,
    submit,
  };
}
