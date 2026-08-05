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

/** 邮箱验证码登录只提交服务端确认的幂等标识、邮箱和 6 位验证码。 */
export interface EmailCodeLoginRequest {
  clientRequestId: string;
  code: string;
  email: string;
}

export type VerificationPurpose = 'LOGIN' | 'REGISTER';

export interface SendEmailCodeRequest {
  email: string;
  purpose: VerificationPurpose;
}

export interface SendEmailCodeResponse {
  cooldownSeconds: number;
  expiresInSeconds: number;
}

/** 注册请求只包含服务端已确认字段，不允许前端指定用户 ID、角色或账号状态。 */
export interface RegisterRequest {
  clientRequestId: string;
  code: string;
  email: string;
  inviteCode: string;
  nickname?: string;
  password: string;
  privacyAccepted: boolean;
  privacyPolicyVersion: string;
}

/** 当前注册页面对应的已确认隐私政策版本，后端仍会做最终一致性校验。 */
export const CURRENT_PRIVACY_POLICY_VERSION = '2026-08-03';

export interface LogoutResult {
  loggedOut: boolean;
}
