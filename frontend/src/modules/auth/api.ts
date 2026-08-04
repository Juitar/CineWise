import { ApiError } from '../../shared/api/ApiError';
import { apiRequest, clearCsrfToken } from '../../shared/api/client';
import type { CurrentUser, LogoutResult, PasswordLoginRequest } from './types';

/** 调用用户或管理员密码登录入口；成功和结果未知时清除匿名阶段 CSRF Token。 */
export async function submitPasswordLogin(request: PasswordLoginRequest): Promise<CurrentUser> {
  try {
    const currentUser = await apiRequest<CurrentUser>('/api/v1/auth/login/password', {
      body: request,
      method: 'POST',
    });
    clearCsrfToken();
    return currentUser;
  } catch (error) {
    if (error instanceof ApiError && error.isResultUnknown) {
      clearCsrfToken();
    }
    throw error;
  }
}

/** 查询服务端当前会话；调用方可关闭全局 401 流程用于启动和结果恢复。 */
export function fetchCurrentUser(handleUnauthorized = false): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/api/v1/auth/me', { handleUnauthorized });
}

/** 执行幂等登出；成功或结果未知时都清除旧 CSRF Token。 */
export async function submitLogout(): Promise<LogoutResult> {
  try {
    const result = await apiRequest<LogoutResult>('/api/v1/auth/logout', { method: 'POST' });
    clearCsrfToken();
    return result;
  } catch (error) {
    if (error instanceof ApiError && error.isResultUnknown) {
      clearCsrfToken();
    }
    throw error;
  }
}
