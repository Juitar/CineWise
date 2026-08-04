import type { PropsWithChildren } from 'react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { ApiError } from '../api/ApiError';
import { setUnauthorizedHandler } from '../api/client';
import { fetchCurrentUser, submitLogout, submitPasswordLogin } from '../../modules/auth/api';
import type { CurrentUser, PasswordLoginRequest } from '../../modules/auth/types';

export type AuthSessionStatus = 'anonymous' | 'authenticated' | 'checking' | 'error';

interface AuthContextValue {
  currentUser: CurrentUser | null;
  login(request: PasswordLoginRequest): Promise<CurrentUser>;
  logout(): Promise<void>;
  recoverSession(): Promise<CurrentUser | null>;
  retrySessionCheck(): Promise<void>;
  status: AuthSessionStatus;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function isAnonymousSession(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.code === 201006);
}

/**
 * 维护全局运行时身份，唯一可信来源是 `/api/v1/auth/me`。
 * Provider 不持久化 Cookie、JWT、密码或 CSRF Token。
 */
export function AuthProvider({ children }: PropsWithChildren) {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [status, setStatus] = useState<AuthSessionStatus>('checking');

  const clearSession = useCallback(() => {
    setCurrentUser(null);
    setStatus('anonymous');
  }, []);

  const recoverSession = useCallback(async (): Promise<CurrentUser | null> => {
    try {
      const user = await fetchCurrentUser(false);
      setCurrentUser(user);
      setStatus('authenticated');
      return user;
    } catch (error) {
      if (isAnonymousSession(error)) {
        clearSession();
        return null;
      }
      throw error;
    }
  }, [clearSession]);

  const retrySessionCheck = useCallback(async () => {
    setStatus('checking');
    try {
      await recoverSession();
    } catch {
      setStatus('error');
    }
  }, [recoverSession]);

  useEffect(() => {
    let active = true;
    fetchCurrentUser(false)
      .then((user) => {
        if (active) {
          setCurrentUser(user);
          setStatus('authenticated');
        }
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }
        if (isAnonymousSession(error)) {
          clearSession();
        } else {
          setStatus('error');
        }
      });
    return () => {
      active = false;
    };
  }, [clearSession]);

  useEffect(() => setUnauthorizedHandler(clearSession), [clearSession]);

  const login = useCallback(async (request: PasswordLoginRequest): Promise<CurrentUser> => {
    await submitPasswordLogin(request);
    const user = await fetchCurrentUser(false);
    setCurrentUser(user);
    setStatus('authenticated');
    return user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await submitLogout();
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      currentUser,
      login,
      logout,
      recoverSession,
      retrySessionCheck,
      status,
    }),
    [currentUser, login, logout, recoverSession, retrySessionCheck, status],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** 读取认证运行时状态；只能在 AuthProvider 内使用。 */
export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return value;
}
