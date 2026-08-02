import { expect, test } from '@playwright/test';

test('首页显示应用壳层和唯一主标题', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('link', { name: '妙语购票' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 1, name: '妙语购票' })).toBeVisible();
});

test('未知地址显示 404 页面并允许返回首页', async ({ page }) => {
  await page.goto('/missing-page');

  await expect(page.getByRole('heading', { level: 1, name: '页面不存在' })).toBeVisible();
  await page.getByRole('link', { name: '返回首页' }).click();
  await expect(page).toHaveURL('/');
});
