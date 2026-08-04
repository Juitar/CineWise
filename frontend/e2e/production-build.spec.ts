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

  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1, name: '妙语购票' })).toBeAttached();

  const homeScripts = new Set(loadedScripts);
  await page.getByRole('link', { name: '全部 28 部' }).click();
  await expect(page).toHaveURL('/movies');
  await expect(page.getByRole('heading', { level: 1, name: '影片列表' })).toBeAttached();

  const lazyRouteScripts = [...loadedScripts].filter((url) => !homeScripts.has(url));
  expect(lazyRouteScripts, '进入 /movies 后应加载新的路由脚本').not.toHaveLength(0);
  expect(failedResources, '生产页面不应出现 JS/CSS 加载失败').toEqual([]);
  expect(pageErrors, '生产页面不应出现未处理的脚本错误').toEqual([]);
});
