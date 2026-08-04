import { expect, test } from '@playwright/test';

test('首页显示对应视口的应用壳层和唯一主标题', async ({ page }, testInfo) => {
  await page.goto('/');

  if (testInfo.project.name === 'mobile-chromium') {
    await expect(page.locator('.mobile-user-title')).toHaveText('首页');
  } else {
    await expect(page.getByRole('link', { name: '妙语购票' })).toBeVisible();
  }

  await expect(
    page.getByRole('heading', { level: 1, name: '妙语购票', includeHidden: true }),
  ).toHaveCount(1);
});

test('未知地址显示 404 页面并允许返回首页', async ({ page }) => {
  await page.goto('/missing-page');

  await expect(page.getByRole('heading', { level: 1, name: '页面不存在' })).toBeVisible();
  await page.getByRole('link', { name: '返回首页' }).click();
  await expect(page).toHaveURL('/');
});
