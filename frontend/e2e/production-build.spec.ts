import { expect, test } from '@playwright/test';

const isScriptOrStyle = (url: string) => /\.(?:js|css)(?:[?#]|$)/i.test(url);

test('生产构建可以打开首页并加载影片懒加载页面', async ({ page }) => {
  const failedResources: string[] = [];
  const pageErrors: string[] = [];
  const loadedScripts = new Set<string>();

  page.on('requestfailed', (request) => {
    if (isScriptOrStyle(request.url())) {
      failedResources.push(`${request.url()}：${request.failure()?.errorText ?? '请求失败'}`);
    }
  });
  page.on('response', (response) => {
    const resourceUrl = response.url();
    if (/\.js(?:[?#]|$)/i.test(resourceUrl) && response.ok()) {
      loadedScripts.add(resourceUrl);
    }
    if (isScriptOrStyle(resourceUrl) && !response.ok() && response.status() !== 304) {
      failedResources.push(`${resourceUrl}：HTTP ${response.status()}`);
    }
  });
  page.on('pageerror', (error) => pageErrors.push(error.message));

  const freshness = {
    dataTime: '2026-08-06T09:00:00+08:00',
    degraded: false,
    expiresAt: '2026-08-06T15:00:00+08:00',
    fallbackType: null,
    isExpired: false,
    source: 'NETSTART',
    sourceType: 'LIVE',
  };
  await page.route('**/api/v1/movies?**', async (route) => {
    await route.fulfill({
      body: JSON.stringify({
        code: 0,
        data: {
          ...freshness,
          page: 1,
          records: [
            {
              durationMinutes: 128,
              genres: ['科幻'],
              movieId: '8100001',
              posterUrl: null,
              rating: 8.6,
              title: '星河远征',
            },
          ],
          size: 5,
          total: 1,
        },
        message: 'success',
        traceId: 'production-home-movies',
      }),
      contentType: 'application/json',
      status: 200,
    });
  });
  await page.route('**/api/v1/cinemas?**', async (route) => {
    await route.fulfill({
      body: JSON.stringify({
        code: 0,
        data: {
          ...freshness,
          page: 1,
          records: [
            {
              address: '梅溪湖路 88 号',
              area: '岳麓区',
              cinemaId: '8200001',
              cityCode: '430100',
              name: '长沙星河影城',
            },
          ],
          size: 3,
          total: 1,
        },
        message: 'success',
        traceId: 'production-home-cinemas',
      }),
      contentType: 'application/json',
      status: 200,
    });
  });

  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1, name: '妙语购票' })).toBeAttached();
  await expect(page.getByRole('heading', { level: 3, name: '星河远征' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 3, name: '长沙星河影城' })).toBeVisible();

  const homeContent = page.locator('.home-page-content');
  await expect(homeContent).not.toContainText('杭州');
  await expect(homeContent).not.toContainText('演示距离');
  await expect(homeContent).not.toContainText('¥');
  await expect(homeContent.getByRole('button', { name: '购票' })).toHaveCount(0);

  const homeScripts = new Set(loadedScripts);
  await page.getByRole('link', { name: '查看全部影片' }).click();
  await expect(page).toHaveURL('/movies');
  await expect(page.getByRole('heading', { level: 1, name: '影片列表' })).toBeAttached();

  const lazyRouteScripts = [...loadedScripts].filter((url) => !homeScripts.has(url));
  expect(lazyRouteScripts, '进入 /movies 后应加载新的路由脚本').not.toHaveLength(0);
  expect(failedResources, '生产页面不应出现 JS/CSS 加载失败').toEqual([]);
  expect(pageErrors, '生产页面不应出现未处理的脚本错误').toEqual([]);
});
