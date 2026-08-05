import { expect, test, type Page, type Route } from '@playwright/test';

type Role = 'ADMIN' | 'USER';

interface AuthState {
  authenticated: boolean;
  lastEmailCodeLogin?: { clientRequestId?: string; code?: string; email?: string };
  lastEmailCodePurpose?: string;
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

    if (url.pathname === '/api/v1/auth/email-codes') {
      const request = route.request().postDataJSON() as { purpose?: string };
      state.lastEmailCodePurpose = request.purpose;
      await respond(route, 200, apiResult({ cooldownSeconds: 60, expiresInSeconds: 300 }));
      return;
    }

    if (url.pathname === '/api/v1/auth/login/email') {
      state.authenticated = true;
      state.role = 'USER';
      state.lastEmailCodeLogin = route.request().postDataJSON() as {
        clientRequestId?: string;
        code?: string;
        email?: string;
      };
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

const protectedTransactionRoutes = [
  { label: '订单列表', path: '/orders' },
  { label: '订单详情', path: '/orders/CW2084194500000000001' },
  { label: '模拟支付', path: '/payments/CW2084194500000000001' },
  { label: '支付结果', path: '/payments/CW2084194500000000001/result' },
  { label: '电子票', path: '/tickets/2084194700000000001' },
  { label: '退票与替代场次', path: '/orders/CW2084194500000000001/refund' },
] as const;

for (const transactionRoute of protectedTransactionRoutes) {
  test(`${transactionRoute.label}路由要求登录并在登录后返回原页面`, async ({ page }) => {
    await installAuthApi(page);
    await page.route('**/api/v1/orders**', async (route) => {
      await respond(route, 404, apiResult(null, 205001, '订单不存在'));
    });
    await page.route('**/api/v1/tickets/**', async (route) => {
      await respond(route, 404, apiResult(null, 205002, '电子票不存在'));
    });

    await page.goto(transactionRoute.path);
    await expect(page).toHaveURL(
      new RegExp(`/login\\?returnUrl=${encodeURIComponent(transactionRoute.path)}$`),
    );

    await submitLogin(page, 'user@cinewise.test');
    await expect(page).toHaveURL(transactionRoute.path);
  });
}

test('用户登录后恢复目标页，刷新仍保持登录，普通用户不能进入管理端', async ({ page }) => {
  await installAuthApi(page);
  await page.goto('/profile');

  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fprofile$/);
  await submitLogin(page, 'user@cinewise.test');
  await expect(page).toHaveURL('/profile');
  await expect(page.getByRole('heading', { level: 1, name: '个人中心' })).toBeVisible();
  await expect(page.getByText('u***@cinewise.test')).toBeVisible();

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

  const adminMenuButton = page.getByRole('button', { name: /演示管理员/ });
  await adminMenuButton.focus();
  await page.keyboard.press('Space');
  await expect(page.getByText('退出登录')).toBeVisible();
});

test('桌面端和移动端使用邮箱验证码登录并恢复目标页', async ({ page }) => {
  const state = await installAuthApi(page);
  await page.goto('/login?returnUrl=%2Fprofile');

  await page.getByRole('tab', { name: '验证码登录' }).click();
  await page.getByRole('textbox', { name: '邮箱', exact: true }).fill('user@cinewise.test');
  await page.getByRole('button', { name: '获取验证码' }).click();
  await expect(page.getByText('验证码已发送，5 分钟内有效')).toBeVisible();
  await page.getByRole('textbox', { name: '邮箱验证码' }).fill('123456');
  await page.getByRole('button', { name: '验证码登录' }).click();

  await expect(page).toHaveURL('/profile');
  expect(state.lastEmailCodePurpose).toBe('LOGIN');
  expect(state.lastEmailCodeLogin).toMatchObject({
    code: '123456',
    email: 'user@cinewise.test',
  });
  expect(state.lastEmailCodeLogin?.clientRequestId).toBeTruthy();
});

test('桌面端和移动端从个人中心退出后不能再访问个人中心', async ({ page }) => {
  const state = await installAuthApi(page);
  await page.goto('/login');
  await submitLogin(page, 'user@cinewise.test');
  await expect(page).toHaveURL('/');

  await page.goto('/profile');
  const logoutButton = page.getByRole('button', { name: '退出登录' });
  await expect(logoutButton).toBeVisible();
  await logoutButton.click();
  await expect(page).toHaveURL('/login');
  expect(state.authenticated).toBe(false);

  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fprofile$/);
});
