import type { AuthRole } from '../../modules/auth/types';

const CONTROL_CHARACTER_PATTERN = /[\u0000-\u001f\u007f]/;

function defaultPath(role: AuthRole): string {
  return role === 'ADMIN' ? '/admin' : '/';
}

function isAdminPath(pathname: string): boolean {
  return pathname === '/admin' || pathname.startsWith('/admin/');
}

/**
 * 只接受与当前登录入口和角色匹配的站内相对路径。
 * 协议、双斜线、反斜线、控制字符和登录页自身都会回退到安全默认页。
 */
export function resolveSafeReturnUrl(returnUrl: string | null, role: AuthRole): string {
  const fallback = defaultPath(role);
  if (
    !returnUrl ||
    !returnUrl.startsWith('/') ||
    returnUrl.startsWith('//') ||
    returnUrl.includes('\\') ||
    CONTROL_CHARACTER_PATTERN.test(returnUrl)
  ) {
    return fallback;
  }

  let parsed: URL;
  try {
    parsed = new URL(returnUrl, 'https://cinewise.local');
  } catch {
    return fallback;
  }

  if (parsed.origin !== 'https://cinewise.local') {
    return fallback;
  }
  if (parsed.pathname === '/login' || parsed.pathname === '/admin/login') {
    return fallback;
  }

  const targetsAdmin = isAdminPath(parsed.pathname);
  if ((role === 'ADMIN') !== targetsAdmin) {
    return fallback;
  }

  return `${parsed.pathname}${parsed.search}${parsed.hash}`;
}

/** 为受保护路由生成只包含站内相对地址的登录跳转。 */
export function createLoginRedirect(pathname: string, search: string, hash: string): string {
  const target = `${pathname}${search}${hash}`;
  const params = new URLSearchParams({ returnUrl: target });
  return `/login?${params.toString()}`;
}
