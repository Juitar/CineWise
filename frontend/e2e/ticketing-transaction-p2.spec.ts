import { expect, test, type Page, type Route } from '@playwright/test';

const orderNo = 'CW2084194500000000001';
const orderId = '2084194500000000001';
const showId = '2084194401305432066';
const ticketId = '2084194700000000001';

function apiResult(data: unknown, code = 0, message = 'success') {
  return {
    code,
    data,
    message,
    traceId: 'ticketing-p2-e2e-trace-id',
  };
}

async function fulfillJson(route: Route, status: number, body: unknown): Promise<void> {
  await route.fulfill({
    body: JSON.stringify(body),
    contentType: 'application/json',
    status,
  });
}

function orderSnapshot(status: string = 'PENDING_PAYMENT') {
  return {
    orderId,
    orderNo,
    showId,
    seatIds: ['2084194402305432067'],
    ticketCount: 1,
    unitPrice: '39.00',
    totalAmount: '39.00',
    status,
    expireTime: '2026-08-10T18:15:00+08:00',
    stateVersion: status === 'PENDING_PAYMENT' ? 0 : 1,
    updatedAt: '2026-08-10T18:00:00+08:00',
  };
}

const paymentSuccess = {
  orderId,
  orderNo,
  paymentNo: 'PAY2084194600000000001',
  orderStatus: 'PAID',
  paymentStatus: 'SUCCESS',
  ticketId,
  stateVersion: 2,
  updatedAt: '2026-08-10T18:05:00+08:00',
};

async function installAuthenticatedUser(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/csrf*', async (route) => {
    await fulfillJson(
      route,
      200,
      apiResult({ headerName: 'X-XSRF-TOKEN', token: 'ticketing-p2-csrf-token' }),
    );
  });
  await page.route('**/api/v1/auth/me*', async (route) => {
    await fulfillJson(
      route,
      200,
      apiResult({
        emailMasked: 'u***@cinewise.test',
        emailVerified: true,
        id: '1001',
        nickname: '票务联调用户',
        role: 'USER',
        status: 'NORMAL',
      }),
    );
  });
}

test.beforeEach(async ({ page }) => {
  await installAuthenticatedUser(page);
});

test('订单列表进入详情并取消待支付订单', async ({ page }) => {
  let currentOrder = orderSnapshot();
  await page.route('**/api/v1/orders**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (pathname === '/api/v1/orders' && request.method() === 'GET') {
      await fulfillJson(
        route,
        200,
        apiResult({ total: 1, page: 1, size: 10, records: [currentOrder] }),
      );
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}` && request.method() === 'GET') {
      await fulfillJson(route, 200, apiResult(currentOrder));
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}/cancel` && request.method() === 'POST') {
      expect(request.headerValue('Idempotency-Key')).toBeTruthy();
      currentOrder = orderSnapshot('CANCELLED');
      await fulfillJson(route, 200, apiResult(currentOrder));
      return;
    }
    await route.fallback();
  });

  await page.goto('/orders');
  await expect(page.getByRole('heading', { level: 1, name: '我的订单' })).toBeVisible();
  await page.getByRole('button', { name: '查看详情' }).click();
  await expect(page).toHaveURL(`/orders/${orderNo}`);
  await page.getByRole('button', { name: '取消订单' }).click();
  await expect(page.getByText('已取消', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '取消订单' })).not.toBeVisible();
});

test('本地校验模拟密码后以空业务请求体完成支付并进入结果页', async ({ page }) => {
  await page.route('**/api/v1/orders**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (pathname === `/api/v1/orders/${orderNo}` && request.method() === 'GET') {
      await fulfillJson(route, 200, apiResult(orderSnapshot()));
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}/payments` && request.method() === 'POST') {
      expect(request.headerValue('Idempotency-Key')).toBeTruthy();
      expect(request.postData()).toBeNull();
      await fulfillJson(route, 200, apiResult(paymentSuccess));
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}/payment` && request.method() === 'GET') {
      await fulfillJson(route, 200, apiResult(paymentSuccess));
      return;
    }
    await route.fallback();
  });

  await page.goto(`/payments/${orderNo}`);
  await page.getByLabel('六位模拟支付密码').fill('123456');
  await page.getByRole('button', { name: '确认支付' }).click();
  await expect(page).toHaveURL(`/payments/${orderNo}/result`);
  await expect(page.getByText('支付成功')).toBeVisible();
});

test('支付响应未知时只查询原支付结果，不重复发送支付 POST', async ({ page }) => {
  let paymentPostCount = 0;
  await page.route('**/api/v1/orders**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (pathname === `/api/v1/orders/${orderNo}` && request.method() === 'GET') {
      await fulfillJson(route, 200, apiResult(orderSnapshot()));
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}/payments` && request.method() === 'POST') {
      paymentPostCount += 1;
      await fulfillJson(route, 502, apiResult(null, 502000, '网关响应未知'));
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}/payment` && request.method() === 'GET') {
      await fulfillJson(route, 200, apiResult(paymentSuccess));
      return;
    }
    await route.fallback();
  });

  await page.goto(`/payments/${orderNo}`);
  await page.getByLabel('六位模拟支付密码').fill('123456');
  await page.getByRole('button', { name: '确认支付' }).click();
  await expect(page.getByRole('button', { name: '重新查询订单结果' })).toBeVisible();
  await page.getByRole('button', { name: '重新查询订单结果' }).click();
  await expect(page).toHaveURL(`/payments/${orderNo}/result`);
  expect(paymentPostCount).toBe(1);
});

test('电子票页面只读取服务端电子票并本地展示二维码', async ({ page }) => {
  await page.route(`**/api/v1/tickets/${ticketId}`, async (route) => {
    await fulfillJson(
      route,
      200,
      apiResult({
        ticketId,
        ticketCode: 'TKT2084194700000000001',
        orderId,
        orderNo,
        showId,
        seatIds: ['2084194402305432067'],
        status: 'VALID',
        qrPayload: 'cinewise:ticket:TKT2084194700000000001',
        issuedAt: '2026-08-10T18:05:00+08:00',
        stateVersion: 0,
        updatedAt: '2026-08-10T18:05:00+08:00',
      }),
    );
  });

  await page.goto(`/tickets/${ticketId}`);
  await expect(page.getByText('有效票可入场')).toBeVisible();
  await expect(page.getByLabel('有效电子票二维码')).toBeVisible();
});

test('退票确认提交服务端影响摘要，并在成功后展示退款结果', async ({ page }) => {
  await page.route('**/api/v1/orders/**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (pathname === `/api/v1/orders/${orderNo}/refund-confirmation`) {
      await fulfillJson(
        route,
        200,
        apiResult({
          orderId,
          orderNo,
          refundAmount: '39.00',
          orderStatus: 'PAID',
          ticketStatus: 'VALID',
          showStartTime: '2026-08-10T19:30:00+08:00',
          orderVersion: 2,
          ticketVersion: 0,
          impactText: '退票成功后电子票失效，原座位将重新开放。',
        }),
      );
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}/alternative-shows`) {
      await fulfillJson(route, 200, apiResult({ orderNo, shows: [] }));
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}/refund` && request.method() === 'GET') {
      await fulfillJson(route, 404, apiResult(null, 205001, '退款不存在'));
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}/refunds` && request.method() === 'POST') {
      const requestBody = request.postDataJSON() as Record<string, unknown>;
      expect(request.headerValue('Idempotency-Key')).toBeTruthy();
      expect(requestBody.actionId).toBeUndefined();
      await fulfillJson(
        route,
        200,
        apiResult({
          refundId: '2084194800000000001',
          refundNo: 'RFD2084194800000000001',
          orderId,
          orderNo,
          refundStatus: 'SUCCESS',
          refundAmount: '39.00',
          orderStatus: 'REFUNDED',
          ticketStatus: 'REFUNDED',
          stateVersion: 4,
          updatedAt: '2026-08-10T18:10:00+08:00',
        }),
      );
      return;
    }
    await route.fallback();
  });

  await page.goto(`/orders/${orderNo}/refund`);
  await expect(page.getByText('预计退款金额')).toBeVisible();
  await page.getByLabel('退票原因输入').fill('行程变化');
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: '确认申请退票' }).click();
  await expect(page.getByText('退票申请成功')).toBeVisible();
});

test('替代场次只使用服务端返回的关联 ID 回流选座页', async ({ page }) => {
  await page.route('**/api/v1/orders/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname === `/api/v1/orders/${orderNo}/refund-confirmation`) {
      await fulfillJson(
        route,
        200,
        apiResult({
          orderId,
          orderNo,
          refundAmount: '39.00',
          orderStatus: 'PAID',
          ticketStatus: 'VALID',
          showStartTime: '2026-08-10T19:30:00+08:00',
          orderVersion: 2,
          ticketVersion: 0,
          impactText: '退票成功后电子票失效，原座位将重新开放。',
        }),
      );
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}/alternative-shows`) {
      await fulfillJson(
        route,
        200,
        apiResult({
          orderNo,
          shows: [
            {
              showId: '2084194401305432099',
              movieId: '2084194398004512769',
              cinemaId: '2084194399128586242',
              startTime: '2026-08-10T21:30:00+08:00',
              basePrice: '39.00',
              status: 'ON_SALE',
              availableSeatCount: 80,
            },
          ],
        }),
      );
      return;
    }
    if (pathname === `/api/v1/orders/${orderNo}/refund`) {
      await fulfillJson(route, 404, apiResult(null, 205001, '退款不存在'));
      return;
    }
    await route.fallback();
  });

  await page.goto(`/orders/${orderNo}/refund`);
  await page.getByRole('button', { name: '选择此场' }).click();
  await expect(page).toHaveURL(
    '/shows/2084194401305432099/seats?movieId=2084194398004512769&cinemaId=2084194399128586242',
  );
});
