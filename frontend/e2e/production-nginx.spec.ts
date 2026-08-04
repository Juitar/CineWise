import { expect, test } from '@playwright/test';

const JAVASCRIPT_CONTENT_TYPE = /(?:application|text)\/javascript/i;

function extractLocalAssetPaths(indexHtml: string, baseURL: string): string[] {
  const assetReferences = [
    ...indexHtml.matchAll(/<script\b[^>]*\bsrc=["']([^"']+)["']/gi),
    ...indexHtml.matchAll(/<link\b[^>]*\bhref=["']([^"']+)["']/gi),
  ].map((match) => match[1]);
  const applicationOrigin = new URL(baseURL).origin;

  return [...new Set(assetReferences)]
    .map((reference) => new URL(reference, baseURL))
    .filter((assetURL) => assetURL.origin === applicationOrigin)
    .map((assetURL) => `${assetURL.pathname}${assetURL.search}`);
}

function expectedContentType(assetPath: string): RegExp {
  const extension = new URL(assetPath, 'http://localhost').pathname.split('.').pop()?.toLowerCase();

  switch (extension) {
    case 'js':
    case 'mjs':
      return JAVASCRIPT_CONTENT_TYPE;
    case 'css':
      return /^text\/css\b/i;
    case 'png':
    case 'gif':
    case 'svg':
    case 'webp':
    case 'avif':
      return new RegExp(`^image\/${extension === 'svg' ? 'svg\\+xml' : extension}\\b`, 'i');
    case 'jpg':
    case 'jpeg':
      return /^image\/jpeg\b/i;
    case 'ico':
      return /^image\/(?:x-icon|vnd\.microsoft\.icon)\b/i;
    case 'woff':
    case 'woff2':
    case 'ttf':
    case 'otf':
      return new RegExp(
        `^(?:font\/${extension}|application\/(?:font-${extension}|x-font-${extension}))\\b`,
        'i',
      );
    case 'eot':
      return /^application\/vnd\.ms-fontobject\b/i;
    case 'wasm':
      return /^application\/wasm\b/i;
    case 'map':
      return /^application\/json\b/i;
    default:
      return /^(?!text\/html\b).+/i;
  }
}

test('production Nginx preserves SPA fallback and rejects missing assets', async ({ request }) => {
  const routeResponse = await request.get('/movies');
  expect(routeResponse.status()).toBe(200);
  expect(routeResponse.headers()['content-type']).toContain('text/html');
  expect(routeResponse.headers()['cache-control']).toContain('no-store');

  const missingJavaScript = await request.get('/definitely-missing.js');
  expect(missingJavaScript.status()).toBe(404);

  const missingStylesheet = await request.get('/definitely-missing.css');
  expect(missingStylesheet.status()).toBe(404);
});

test('API paths are proxied instead of handled by the SPA fallback', async ({ request }) => {
  const apiResponse = await request.get('/api/production-nginx-smoke');

  expect(apiResponse.status()).toBe(200);
  expect(apiResponse.headers()['content-type']).toContain('application/json');
  expect(apiResponse.headers()['x-cinewise-api-stub']).toBe('true');
});

test('index assets exist with browser-compatible MIME types', async ({ request }, testInfo) => {
  const baseURL = testInfo.project.use.baseURL;
  expect(baseURL).toBeTruthy();

  const indexResponse = await request.get('/index.html');
  expect(indexResponse.status()).toBe(200);
  expect(indexResponse.headers()['content-type']).toContain('text/html');
  expect(indexResponse.headers()['cache-control']).toContain('no-store');

  const assetPaths = extractLocalAssetPaths(await indexResponse.text(), baseURL!);
  expect(assetPaths.length).toBeGreaterThan(0);

  for (const assetPath of assetPaths) {
    const assetResponse = await request.get(assetPath);
    const contentType = assetResponse.headers()['content-type'] ?? '';

    expect(assetResponse.status(), `${assetPath} should exist`).toBe(200);
    expect(contentType, `${assetPath} returned ${contentType}`).toMatch(
      expectedContentType(assetPath),
    );
  }
});

test('browser can boot the production bundle on a nested route', async ({ page }) => {
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));

  const response = await page.goto('/movies', { waitUntil: 'networkidle' });

  expect(response?.status()).toBe(200);
  await expect(page.locator('#root')).toBeAttached();
  expect(pageErrors).toEqual([]);
});
