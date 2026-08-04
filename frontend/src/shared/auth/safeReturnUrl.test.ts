import { describe, expect, it } from 'vitest';

import { createLoginRedirect, resolveSafeReturnUrl } from './safeReturnUrl';

describe('safeReturnUrl', () => {
  it('保留当前角色允许的站内路径和查询参数', () => {
    expect(resolveSafeReturnUrl('/profile?tab=orders#latest', 'USER')).toBe(
      '/profile?tab=orders#latest',
    );
    expect(resolveSafeReturnUrl('/admin/orders', 'ADMIN')).toBe('/admin/orders');
  });

  it.each([
    'https://evil.example/path',
    '//evil.example/path',
    '/\\evil',
    '/profile\u0000',
    '/login',
  ])('拒绝外部、控制字符或循环登录地址：%s', (returnUrl) => {
    expect(resolveSafeReturnUrl(returnUrl, 'USER')).toBe('/');
  });

  it('按服务端角色限制回跳范围并选择默认页面', () => {
    expect(resolveSafeReturnUrl('/admin/orders', 'USER')).toBe('/');
    expect(resolveSafeReturnUrl('/movies', 'ADMIN')).toBe('/admin');
    expect(resolveSafeReturnUrl(null, 'ADMIN')).toBe('/admin');
  });

  it('为所有守卫生成统一登录入口的编码回跳地址', () => {
    expect(createLoginRedirect('/profile', '?tab=orders', '#latest')).toBe(
      '/login?returnUrl=%2Fprofile%3Ftab%3Dorders%23latest',
    );
    expect(createLoginRedirect('/admin/orders', '', '')).toBe('/login?returnUrl=%2Fadmin%2Forders');
  });
});
