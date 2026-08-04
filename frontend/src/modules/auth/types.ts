export type AuthRole = 'ADMIN' | 'USER';
export type AccountStatus = 'DISABLED' | 'LOCKED' | 'NORMAL';

/** 浏览器可见的当前用户摘要，不包含完整邮箱、JWT 或 tokenVersion。 */
export interface CurrentUser {
  emailMasked: string;
  emailVerified: boolean;
  id: string;
  nickname: string;
  privacyPolicyVersion: string;
  role: AuthRole;
  status: AccountStatus;
}

/** 密码登录请求只包含后端已确认的三个字段。 */
export interface PasswordLoginRequest {
  clientRequestId: string;
  email: string;
  password: string;
}

export interface LogoutResult {
  loggedOut: boolean;
}
