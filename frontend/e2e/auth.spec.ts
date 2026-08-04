import { expect, test, type Page, type Route } from '@playwright/test';

type Role = 'ADMIN' | 'USER';

interface AuthState {
  authenticated: boolean;
  role: Role;
}

function apiResult(data: unknown, code = 0, message = 'success') {
  return {
    code,
    data,
    message,
    traceId: 'e2e-trace-id',
  };
}

async function respond(route: Route, status: number, body: unknown) {
  await route.fulfill({
    body: JSON.stringify(body),
    contentType: 'application/json',
    status,
  });
}

async function installAuthApi(page: Page, initialRole: Role = 'USER'): Promise<AuthState> {
  const state: AuthState = { authenticated: false, role: initialRole };

  await page.route('**/api/v1/auth/**', async (route) => {
    const url = new URL(route.request().url());

    if (url.pathname === '/api/v1/auth/csrf') {
      await respond(route, 200, apiResult({ headerName: 'X-XSRF-TOKEN', token: 'e2e-csrf-token' }));
      return;
    }

    if (url.pathname === '/api/v1/auth/me') {
      if (!state.authenticated) {
        await respond(route, 401, apiResult(null, 201006, '登录状态已失效'));
        return;
      }
      await respond(
        route,
        200,
        apiResult({
          emailMasked: state.role === 'ADMIN' ? 'a***@cinewise.test' : 'u***@cinewise.test',
          emailVerified: true,
          id: state.role === 'ADMIN' ? '2001' : '1001',
          nickname: state.role === 'ADMIN' ? '演示管理员' : '演示用户',
          privacyPolicyVersion: '2026-08-03',
          role: state.role,
          status: 'NORMAL',
        }),
      );
      return;
    }

    if (url.pathname === '/api/v1/auth/login/password') {
      state.authenticated = true;
      const request = route.request().postDataJSON() as { email?: string };
      state.role = request.email?.startsWith('admin@') ? 'ADMIN' : 'USER';
      await respond(route, 200, apiResult({ loggedIn: true }));
      return;
    }

    if (url.pathname === '/api/v1/auth/logout') {
      state.authenticated = false;
      await respond(route, 200, apiResult({ loggedOut: true }));
      return;
    }

    await route.continue();
  });

  return state;
}

async function submitLogin(page: Page, email: string) {
  await page.getByRole('textbox', { name: '邮箱' }).fill(email);
  await page.locator('input[aria-label="密码"]').fill('Password1');
  await page.getByRole('button', { name: /登\s*录/ }).click();
}

test('用户登录后恢复目标页，刷新仍保持登录，普通用户不能进入管理端', async ({ page }) => {
  await installAuthApi(page);
  await page.goto('/profile');

  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fprofile$/);
  await submitLogin(page, 'user@cinewise.test');
  await expect(page).toHaveURL('/profile');
  await expect(page.getByRole('heading', { level: 1, name: '妙语用户' })).toBeVisible();

  await page.reload();
  await expect(page).toHaveURL('/profile');

  await page.goto('/admin');
  await expect(page).toHaveURL('/403');
  await expect(page.getByText('无权访问')).toBeVisible();
});

test('管理员从统一入口登录后进入管理工作台', async ({ page }) => {
  await installAuthApi(page, 'ADMIN');
  await page.goto('/admin');

  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fadmin$/);
  await submitLogin(page, 'admin@cinewise.test');
  await expect(page).toHaveURL('/admin');
  await expect(page.getByText('内容同步状态').first()).toBeVisible();
});

test('桌面端退出后旧会话不能再访问个人中心', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-chromium', '移动壳层暂未提供退出菜单');

  const state = await installAuthApi(page);
  await page.goto('/login');
  await submitLogin(page, 'user@cinewise.test');
  await expect(page).toHaveURL('/');

  await page.getByText('演示用户').click();
  await page.getByText('退出登录').click();
  await expect(page).toHaveURL('/login');
  expect(state.authenticated).toBe(false);

  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fprofile$/);
});
