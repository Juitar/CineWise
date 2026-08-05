import { expect, test, type Page, type Route } from '@playwright/test';

const orderNo = 'CW2084194500000000001';
const ticketId = '2084194700000000001';

function apiResult(data: unknown) {
  return {
    code: 0,
    data,
    message: 'success',
    traceId: 'order-show-context-e2e-trace-id',
  };
}

async function fulfillJson(route: Route, body: unknown): Promise<void> {
  await route.fulfill({
    body: JSON.stringify(body),
    contentType: 'application/json',
    status: 200,
  });
}

async function installAuthenticatedUser(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/me*', async (route) => {
    await fulfillJson(
      route,
      apiResult({
        emailMasked: 'u***@cinewise.test',
        emailVerified: true,
        id: '1001',
        nickname: '订单上下文联调用户',
        role: 'USER',
        status: 'NORMAL',
      }),
    );
  });
}

test.beforeEach(async ({ page }) => {
  await installAuthenticatedUser(page);
});

test('电子票按服务端 orderNo 查询订单场次上下文，不伪造影片内容', async ({ page }) => {
  let orderDetailRequestCount = 0;
  await page.route(`**/api/v1/tickets/${ticketId}`, async (route) => {
    await fulfillJson(
      route,
      apiResult({
        issuedAt: '2026-08-10T18:05:00+08:00',
        orderId: '2084194500000000001',
        orderNo,
        qrPayload: 'cinewise:ticket:TKT2084194700000000001',
        seatIds: ['2084194402305432067'],
        showId: '2084194401305432066',
        stateVersion: 0,
        status: 'VALID',
        ticketCode: 'TKT2084194700000000001',
        ticketId,
        updatedAt: '2026-08-10T18:05:00+08:00',
      }),
    );
  });
  await page.route(`**/api/v1/orders/${orderNo}`, async (route) => {
    orderDetailRequestCount += 1;
    await fulfillJson(
      route,
      apiResult({
        cinemaId: '2084194399128586242',
        expireTime: '2026-08-10T18:15:00+08:00',
        movieId: '2084194398004512769',
        orderId: '2084194500000000001',
        orderNo,
        seatIds: ['2084194402305432067'],
        showId: '2084194401305432066',
        showStartTime: '2026-08-10T19:30:00+08:00',
        stateVersion: 2,
        status: 'PAID',
        ticketCount: 1,
        totalAmount: '39.00',
        unitPrice: '39.00',
        updatedAt: '2026-08-10T18:05:00+08:00',
      }),
    );
  });

  await page.goto(`/tickets/${ticketId}`);

  await expect(page.getByText('有效票可入场')).toBeVisible();
  await expect(page.getByText('2026-08-10 19:30')).toBeVisible();
  await expect(page.getByText('影片信息暂不可用')).toBeVisible();
  await expect(page.getByText('场次编号：2084194401305432066')).toBeVisible();
  expect(orderDetailRequestCount).toBe(1);
});
