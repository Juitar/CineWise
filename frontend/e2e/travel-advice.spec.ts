import { expect, test } from '@playwright/test';
import adviceFixture from '../../backend/src/test/resources/fixtures/travel/c/advice-weather-normal.json';
import reminderFixture from '../../backend/src/test/resources/fixtures/travel/c/reminder-update-success.json';
import taskFixture from '../../backend/src/test/resources/fixtures/travel/c/task-detail-success.json';

function envelope(data: unknown) {
  return { code: 0, data, message: 'success', traceId: 'travel-e2e-trace' };
}

async function mockAuth(page: import('@playwright/test').Page) {
  await page
    .context()
    .addCookies([
      { name: 'access_token', value: 'http-only-placeholder', url: 'http://127.0.0.1:8123' },
    ]);
  await page.route('**/api/v1/auth/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/auth/me') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope({
            emailMasked: 'u***@example.com',
            emailVerified: true,
            id: '10001',
            nickname: '测试用户',
            privacyPolicyVersion: '2026-08-03',
            role: 'USER',
            status: 'NORMAL',
          }),
        ),
      });
      return;
    }
    if (path === '/api/v1/auth/csrf') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(envelope({ headerName: 'X-XSRF-TOKEN', token: 'travel-csrf' })),
      });
      return;
    }
    await route.fallback();
  });
}

test.beforeEach(async ({ page }) => {
  await mockAuth(page);
});

test('桌面和移动端展示真实建议并按版本更新提醒', async ({ page }) => {
  let reminderWrites = 0;
  await page.route('**/api/v1/travel/tasks/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/v1/travel/tasks/90001' && request.method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(taskFixture),
      });
      return;
    }
    if (path === '/api/v1/travel/tasks/90001/advice' && request.method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(adviceFixture),
      });
      return;
    }
    if (path === '/api/v1/travel/tasks/90001/reminder' && request.method() === 'PUT') {
      reminderWrites += 1;
      expect(request.headers()['if-match']).toBe('"1"');
      expect(request.headers()['x-xsrf-token']).toBe('travel-csrf');
      expect(request.postDataJSON()).toEqual({
        triggerAt: '2026-08-07T18:30:00+08:00',
        version: 1,
      });
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...reminderFixture,
          data: { ...reminderFixture.data, triggerAt: '2026-08-07T18:30:00+08:00' },
        }),
      });
      return;
    }
    await route.fallback();
  });

  await page.goto('/travel/90001');
  await expect(page.getByRole('heading', { name: '观影出行建议' })).toBeVisible();
  await expect(page.getByRole('listitem').filter({ hasText: '关注短时降雨' })).toBeVisible();
  await expect(page.getByText(/AMAP_WEATHER/)).toBeVisible();
  await expect(page.getByText(/杭州UME|1.2km|店内餐饮|路线预览/)).toHaveCount(0);

  await page.getByLabel('修改提醒时间').fill('2026-08-07T18:30');
  await page.getByRole('button', { name: '更新提醒时间' }).click();
  await expect(page.getByText('提醒时间已更新')).toBeVisible();
  expect(reminderWrites).toBe(1);
});

test('任务不存在时只返回订单列表', async ({ page }) => {
  await page.route('**/api/v1/travel/tasks/**', async (route) => {
    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 207001,
        data: null,
        message: '出行任务不存在或无权访问',
        traceId: 'travel-not-found',
      }),
    });
  });
  await page.goto('/travel/90001');
  await expect(page.getByText('出行任务不存在或无访问权限')).toBeVisible();
  await page.getByRole('button', { name: '返回订单' }).click();
  await expect(page).toHaveURL(/\/orders$/);
});
