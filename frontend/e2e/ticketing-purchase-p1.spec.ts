import { expect, test } from '@playwright/test';

const showListPayload = {
  code: 0,
  message: 'success',
  data: [
    {
      showId: '2084194401305432066',
      movieId: '2084194398004512769',
      cinemaId: '2084194399128586242',
      cinemaName: '星河影城（新天地店）',
      auditoriumId: '2084194400215384065',
      auditoriumName: '1号激光全景厅',
      startTime: '2026-08-03T19:00:00+08:00',
      endTime: '2026-08-03T21:15:00+08:00',
      expiresAt: '2026-08-03T18:45:00+08:00',
      languageVersion: '原版 3D',
      basePrice: '39.00',
      availableSeatCount: 80,
      status: 'ON_SALE',
      dataType: 'INITIAL',
      stateVersion: 1,
      updatedAt: '2026-08-03T10:00:00+08:00',
    },
  ],
  traceId: '11111111111111111111111111111111',
};

const seatMapPayload = {
  code: 0,
  message: 'success',
  data: {
    showId: '2084194401305432066',
    auditoriumId: '2084194400215384065',
    auditoriumName: '1号激光全景厅',
    rowCount: 10,
    seatCount: 100,
    availableSeatCount: 98,
    stateVersion: 2,
    updatedAt: '2026-08-03T12:00:00+08:00',
    seats: [
      {
        seatId: '2084194402305432067',
        rowNo: '5',
        seatNo: '6',
        seatLabel: '5排6座',
        status: 'AVAILABLE',
        stateVersion: 0,
      },
      {
        seatId: '2084194402305432068',
        rowNo: '5',
        seatNo: '7',
        seatLabel: '5排7座',
        status: 'LOCKED',
        stateVersion: 1,
      },
    ],
  },
  traceId: '22222222222222222222222222222222',
};

const createOrderPayload = {
  code: 0,
  message: 'success',
  data: {
    orderId: '2084194500000000001',
    orderNo: 'CW2084194500000000001',
    showId: '2084194401305432066',
    seatIds: ['2084194402305432067'],
    ticketCount: 1,
    unitPrice: '39.00',
    totalAmount: '39.00',
    status: 'PENDING_PAYMENT',
    expireTime: '2026-08-03T18:15:00+08:00',
    stateVersion: 0,
    updatedAt: '2026-08-03T18:00:00+08:00',
  },
  traceId: '33333333333333333333333333333333',
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/auth/csrf*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'success',
        data: { headerName: 'X-XSRF-TOKEN', token: 'e2e-csrf-token' },
        traceId: 'csrf-111111111111111111111111111',
      }),
    });
  });
  await page.route('**/api/v1/auth/me*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'success',
        data: {
          id: '1001',
          nickname: '演示用户',
          emailMasked: 'u***@cinewise.test',
          emailVerified: true,
          role: 'USER',
        },
        traceId: 'auth-me-111111111111111111111111',
      }),
    });
  });
});

test('场次页面 /shows 能正常拉取并显示可售场次', async ({ page }) => {
  await page.route('**/api/v1/shows*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(showListPayload),
    });
  });

  await page.goto('/shows?movieId=2084194398004512769&cinemaId=2084194399128586242');
  await expect(page.getByRole('heading', { level: 1, name: '选择场次' })).toBeVisible();
  await expect(page.getByText('1号激光全景厅')).toBeVisible();
  await expect(page.getByRole('button', { name: /去选座/ })).toBeVisible();
});

test('选座页面能交互选座并以重复 seatId 格式导航到订单确认页', async ({ page }) => {
  await page.route('**/api/v1/shows/2084194401305432066/seats*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(seatMapPayload),
    });
  });

  await page.goto(
    '/shows/2084194401305432066/seats?movieId=2084194398004512769&cinemaId=2084194399128586242',
  );
  await expect(page.getByText('1号激光全景厅 银幕方向')).toBeVisible();

  // 点击 5排6座 的按钮
  const seatBtn = page.getByRole('checkbox', { name: /5排6座/ });
  await seatBtn.click();

  // 点击确认选座
  await page.getByRole('button', { name: '确认选座' }).click();

  // 验证 URL 中具有重复参数格式 (&seatId=2084194402305432067)
  await expect(page).toHaveURL(/.*\/orders\/confirm\?.*seatId=2084194402305432067/);
});

test('订单确认页正常提交 POST /api/v1/orders 并展现成功订单信息', async ({ page }) => {
  await page.route('**/api/v1/shows*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(showListPayload),
    });
  });

  await page.route('**/api/v1/shows/2084194401305432066/seats*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(seatMapPayload),
    });
  });

  await page.route('**/api/v1/orders', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(createOrderPayload),
    });
  });

  await page.goto(
    '/orders/confirm?showId=2084194401305432066&movieId=2084194398004512769&cinemaId=2084194399128586242&seatId=2084194402305432067',
  );

  await expect(page.getByRole('heading', { level: 1, name: '确认订单信息' })).toBeVisible();
  await page.getByRole('button', { name: '确认并提交订单' }).click();

  await expect(page.getByText('订单创建成功！')).toBeVisible();
  await expect(page.getByText('CW2084194500000000001')).toBeVisible();
});

test('订单确认页发生 RESULT_UNKNOWN (502) 时自动发起等幂查询恢复而不再发 POST', async ({
  page,
}) => {
  await page.route('**/api/v1/shows*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(showListPayload),
    });
  });

  await page.route('**/api/v1/shows/2084194401305432066/seats*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(seatMapPayload),
    });
  });

  let postCount = 0;
  await page.route('**/api/v1/orders', async (route) => {
    postCount++;
    await route.fulfill({
      status: 502,
      contentType: 'text/html',
      body: '502 Bad Gateway',
    });
  });

  await page.route('**/api/v1/orders/by-request/*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(createOrderPayload),
    });
  });

  await page.goto(
    '/orders/confirm?showId=2084194401305432066&movieId=2084194398004512769&cinemaId=2084194399128586242&seatId=2084194402305432067',
  );

  await page.getByRole('button', { name: '确认并提交订单' }).click();

  // 验证经由 getOrderByRequestId 成功恢复，同时 post 仅被执行了 1 次
  await expect(page.getByText('订单创建成功！')).toBeVisible();
  expect(postCount).toBe(1);
});

test('订单确认页发生座位不可锁定 (409 / 204001) 时呈现专有提示并引导返回重选', async ({ page }) => {
  await page.route('**/api/v1/shows*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(showListPayload),
    });
  });

  await page.route('**/api/v1/shows/2084194401305432066/seats*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(seatMapPayload),
    });
  });

  await page.route('**/api/v1/orders', async (route) => {
    await route.fulfill({
      status: 409,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 204001,
        message: '座位不可锁定',
        data: null,
        traceId: 'conflict-1',
      }),
    });
  });

  await page.goto(
    '/orders/confirm?showId=2084194401305432066&movieId=2084194398004512769&cinemaId=2084194399128586242&seatId=2084194402305432067',
  );
  await page.getByRole('button', { name: '确认并提交订单' }).click();

  await expect(page.getByText('座位不可锁定')).toBeVisible();
  await expect(page.getByRole('button', { name: '重选座位' })).toBeVisible();

  // 409后刷新仍不是RESULT_UNKNOWN，不会进入未知保护态锁死
  await page.reload();
  await expect(page.getByText('重新查询订单结果')).not.toBeVisible();
  await expect(page.getByRole('button', { name: '确认并提交订单' })).toBeVisible();
});

test('订单确认页发生 RESULT_UNKNOWN 且恢复查询 404 后仍禁止第二次 POST，只能通过按钮再次查询', async ({
  page,
}) => {
  await page.route('**/api/v1/shows*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(showListPayload),
    });
  });

  await page.route('**/api/v1/shows/2084194401305432066/seats*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(seatMapPayload),
    });
  });

  let postCount = 0;
  await page.route('**/api/v1/orders', async (route) => {
    postCount++;
    await route.fulfill({
      status: 504,
      contentType: 'text/html',
      body: '504 Gateway Timeout',
    });
  });

  let queryCount = 0;
  await page.route('**/api/v1/orders/by-request/*', async (route) => {
    queryCount++;
    if (queryCount === 1) {
      await route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ code: 100404, message: 'Not found' }),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createOrderPayload),
      });
    }
  });

  await page.goto(
    '/orders/confirm?showId=2084194401305432066&movieId=2084194398004512769&cinemaId=2084194399128586242&seatId=2084194402305432067',
  );
  await page.getByRole('button', { name: '确认并提交订单' }).click();

  // 第一次提交 504 后，查询为 404，页面处于 RESULT_UNKNOWN，禁止重投 POST
  await expect(page.getByText('重新查询订单结果')).toBeVisible();
  await expect(page.getByRole('button', { name: '确认并提交订单' })).not.toBeVisible();
  expect(postCount).toBe(1);
  expect(queryCount).toBe(1);

  // 点击“重新查询订单结果”，调用 query 成功，不触发 POST
  await page.getByRole('button', { name: '重新查询订单结果' }).click();
  await expect(page.getByText('订单创建成功！')).toBeVisible();
  expect(postCount).toBe(1);
  expect(queryCount).toBe(2);
});

test('订单确认页在 RESULT_UNKNOWN 状态下刷新页面，继续使用原 clientRequestId 查询且禁止重发 POST', async ({
  page,
}) => {
  await page.route('**/api/v1/shows*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(showListPayload),
    });
  });

  await page.route('**/api/v1/shows/2084194401305432066/seats*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(seatMapPayload),
    });
  });

  let postCount = 0;
  await page.route('**/api/v1/orders', async (route) => {
    postCount++;
    await route.fulfill({
      status: 504,
      contentType: 'text/html',
      body: '504 Gateway Timeout',
    });
  });

  let queriedRequestId = '';
  await page.route('**/api/v1/orders/by-request/*', async (route) => {
    queriedRequestId = route.request().url().split('/').pop() || '';
    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({ code: 100404, message: 'Not found' }),
    });
  });

  await page.goto(
    '/orders/confirm?showId=2084194401305432066&movieId=2084194398004512769&cinemaId=2084194399128586242&seatId=2084194402305432067',
  );
  await page.getByRole('button', { name: '确认并提交订单' }).click();
  await expect(page.getByText('重新查询订单结果')).toBeVisible();
  const firstRequestId = queriedRequestId;
  expect(firstRequestId).not.toBe('');
  expect(postCount).toBe(1);

  // 刷新页面
  await page.reload();
  await expect(page.getByText('重新查询订单结果')).toBeVisible();
  await expect(page.getByRole('button', { name: '确认并提交订单' })).not.toBeVisible();

  // 再次点击查询
  await page.route('**/api/v1/orders/by-request/*', async (route) => {
    queriedRequestId = route.request().url().split('/').pop() || '';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(createOrderPayload),
    });
  });
  await page.getByRole('button', { name: '重新查询订单结果' }).click();
  await expect(page.getByText('订单创建成功！')).toBeVisible();
  expect(queriedRequestId).toBe(firstRequestId);
  expect(postCount).toBe(1);
});
