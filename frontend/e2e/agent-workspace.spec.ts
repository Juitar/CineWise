import { expect, test } from '@playwright/test';

const sessionId = 'session-example-1';

function envelope(data: unknown, code = 0) {
  return { code, data, message: code === 0 ? 'success' : 'error', traceId: 'agent-e2e-trace' };
}

async function mockAuthenticatedAgent(
  page: import('@playwright/test').Page,
  showConfirmation = false,
  recoverUnknownConfirmation = false,
) {
  let confirmationPostCount = 0;
  let confirmationRunQueryCount = 0;
  let messageHistoryQueryCount = 0;
  await page
    .context()
    .addCookies([
      { name: 'access_token', value: 'http-only-cookie-placeholder', url: 'http://127.0.0.1:4173' },
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
            id: 'user-1',
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
        body: JSON.stringify(envelope({ headerName: 'X-XSRF-TOKEN', token: 'csrf-agent-e2e' })),
      });
      return;
    }
    await route.fallback();
  });

  await page.route('**/api/v1/agent/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === `/api/v1/agent/sessions/${sessionId}/messages/stream`) {
      expect(request.method()).toBe('POST');
      expect(request.headers()['x-xsrf-token']).toBe('csrf-agent-e2e');
      expect(request.headers().cookie).toContain('access_token=');
      const body = request.postDataJSON();
      expect(body).toMatchObject({ content: '推荐一部电影', context: { entry: 'workspace' } });
      const events = [
        {
          eventId: '40',
          sessionId,
          runId: 'run-example-1',
          planId: 'plan-example-1',
          planVersion: 1,
          nodeId: 'rank-movie',
          eventType: 'step.start',
          displayText: '正在查找',
          payload: { status: 'RUNNING' },
          occurredAt: '2026-08-06T10:00:00+08:00',
        },
        {
          eventId: '41',
          sessionId,
          runId: 'run-example-1',
          planId: 'plan-example-1',
          planVersion: 1,
          nodeId: 'render-recommendation',
          eventType: 'card',
          displayText: '已生成推荐卡片',
          payload: {
            type: 'MOVIE_CARD',
            title: '暂未找到可购场次',
            movies: [{ movieId: '1001', title: '示例影片' }],
            source: 'recommendation',
            dataAt: '2026-08-06T10:00:00+08:00',
            expiresAt: '2026-08-06T10:05:00+08:00',
            degraded: true,
            fallbackType: 'SHOWTIME_UNAVAILABLE',
          },
          occurredAt: '2026-08-06T10:00:01+08:00',
        },
        {
          eventId: '42',
          sessionId,
          runId: 'run-example-1',
          planId: 'plan-example-1',
          planVersion: 1,
          nodeId: null,
          eventType: 'run.complete',
          displayText: '运行已完成',
          payload: { status: 'COMPLETED' },
          occurredAt: '2026-08-06T10:00:02+08:00',
        },
      ];
      const bodyText = events
        .map(
          (event) =>
            `id: ${event.eventId}\nevent: ${event.eventType}\ndata: ${JSON.stringify(event)}\n\n`,
        )
        .join('');
      await route.fulfill({ status: 200, contentType: 'text/event-stream', body: bodyText });
      return;
    }
    if (path === `/api/v1/agent/sessions/${sessionId}/messages`) {
      messageHistoryQueryCount += 1;
      const records = showConfirmation
        ? [
            {
              messageId: 'message-confirm-1',
              runId: '52b810c5-4b03-4a41-9c36-07372f1a6f59',
              role: 'ASSISTANT',
              type: 'PLAN_CARD',
              text: '请确认建单',
              payload: {
                type: 'PLAN_CARD',
                actionId: 'action-e2e-1',
                actionType: 'CREATE_ORDER',
                status: 'PENDING_CONFIRMATION',
                title: '确认建单',
                displayLines: ['影片：示例影片', '座位：已选择'],
                expiresAt: '2026-08-08T10:05:00+08:00',
              },
              status: 'COMPLETED',
              completedAt: '2026-08-07T10:00:00+08:00',
              createdAt: '2026-08-07T10:00:00+08:00',
            },
          ]
        : [];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(envelope({ total: records.length, page: 1, size: 100, records })),
      });
      return;
    }
    if (showConfirmation && path === '/api/v1/agent/runs/52b810c5-4b03-4a41-9c36-07372f1a6f59') {
      expect(request.method()).toBe('GET');
      confirmationRunQueryCount += 1;
      const recovered = recoverUnknownConfirmation && confirmationRunQueryCount > 1;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope({
            runId: '52b810c5-4b03-4a41-9c36-07372f1a6f59',
            sessionId,
            status: recovered ? 'COMPLETED' : 'RUNNING',
            planId: 'plan-e2e-1',
            planVersion: 2,
            startedAt: '2026-08-07T10:00:00+08:00',
            finishedAt: recovered ? '2026-08-07T10:01:00+08:00' : null,
            lastEventId: '42',
            messages: [],
            steps: [],
            events: [
              {
                eventId: '42',
                sessionId,
                runId: '52b810c5-4b03-4a41-9c36-07372f1a6f59',
                planId: 'plan-e2e-1',
                planVersion: 2,
                nodeId: 'create-order',
                eventType: 'card',
                displayText: '请确认建单',
                payload: {
                  type: 'PLAN_CARD',
                  actionId: 'action-e2e-1',
                  actionType: 'CREATE_ORDER',
                  status: recovered ? 'SUCCEEDED' : 'PENDING_CONFIRMATION',
                  title: '确认建单',
                  displayLines: ['影片：示例影片', '座位：已选择'],
                  expiresAt: '2026-08-08T10:05:00+08:00',
                },
                occurredAt: '2026-08-07T10:00:00+08:00',
              },
            ],
          }),
        ),
      });
      return;
    }
    if (path === '/api/v1/agent/actions/action-e2e-1/confirm') {
      expect(request.postDataJSON()).toEqual({ confirmed: true });
      confirmationPostCount += 1;
      if (recoverUnknownConfirmation) {
        await route.fulfill({
          status: 503,
          contentType: 'application/json',
          body: JSON.stringify(envelope(null, 306001)),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope({
            actionId: 'action-e2e-1',
            runId: '52b810c5-4b03-4a41-9c36-07372f1a6f59',
            planVersion: 2,
            status: 'SUCCEEDED',
            updatedAt: '2026-08-07T10:01:00+08:00',
          }),
        ),
      });
      return;
    }
    if (path === '/api/v1/agent/runs/52b810c5-4b03-4a41-9c36-07372f1a6f59') {
      expect(request.method()).toBe('GET');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope({
            runId: '52b810c5-4b03-4a41-9c36-07372f1a6f59',
            sessionId,
            status: 'RUNNING',
            planId: 'plan-e2e-1',
            planVersion: 1,
            startedAt: '2026-08-07T10:00:00+08:00',
            finishedAt: null,
            lastEventId: '50',
            messages: [],
            steps: [],
            events: [
              {
                eventId: '50',
                sessionId,
                runId: '52b810c5-4b03-4a41-9c36-07372f1a6f59',
                planId: 'plan-e2e-1',
                planVersion: 1,
                nodeId: 'confirm-order',
                eventType: 'card',
                displayText: '请确认建单',
                payload: {
                  type: 'PLAN_CARD',
                  actionId: 'action-e2e-1',
                  actionType: 'CREATE_ORDER',
                  status: 'PENDING_CONFIRMATION',
                  title: '确认建单',
                  displayLines: ['影片：示例影片', '座位：已选择'],
                  expiresAt: '2026-08-08T10:05:00+08:00',
                },
                occurredAt: '2026-08-07T10:00:00+08:00',
              },
            ],
          }),
        ),
      });
      return;
    }
    if (path === '/api/v1/agent/sessions') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          envelope({
            total: 1,
            page: 1,
            size: 100,
            records: [
              {
                sessionId,
                summary: '周末观影',
                status: 'ACTIVE',
                createdAt: '2026-08-06T09:00:00+08:00',
                updatedAt: '2026-08-06T09:00:00+08:00',
              },
            ],
          }),
        ),
      });
      return;
    }
    await route.fallback();
  });

  return {
    confirmationPostCount: () => confirmationPostCount,
    confirmationRunQueryCount: () => confirmationRunQueryCount,
    messageHistoryQueryCount: () => messageHistoryQueryCount,
  };
}

test('匿名访问 Agent 工作区安全跳转登录', async ({ page }) => {
  await page.route('**/api/v1/auth/me*', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify(envelope(null, 201006)),
    });
  });
  await page.goto(`/assistant/${sessionId}`);
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fassistant%2Fsession-example-1/);
});

test('登录用户消费 POST SSE 并展示类型化降级卡片', async ({ page }) => {
  await mockAuthenticatedAgent(page);
  await page.goto(`/assistant/${sessionId}`);
  await expect(page.getByRole('heading', { name: '妙语观影助手' })).toBeVisible();
  const mobileSessionButton = page.getByRole('button', { name: '会话列表' });
  const mobileLayout = await mobileSessionButton.isVisible().catch(() => false);
  if (mobileLayout) {
    await mobileSessionButton.click();
  }
  await expect(page.getByText('周末观影')).toBeVisible();
  if (mobileLayout) await page.keyboard.press('Escape');
  await page.getByLabel('观影需求').fill('推荐一部电影');
  await page.getByRole('button', { name: /发\s*送/ }).click();
  await expect(page.getByText('暂未找到可购场次')).toBeVisible();
  await expect(page.locator('[data-agent-card-kind="movie-card"]')).toBeVisible();
  await expect(page.getByText('影片推荐')).toBeVisible();
  await expect(page.getByText('示例影片')).toBeVisible();
  await expect(page.getByText('当前结果为降级数据，请注意来源和有效时间')).toBeVisible();
  await expect(page.getByText('recommendation')).toBeVisible();
  await expect(page.getByText('已完成', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /购票|确认|支付/ })).toHaveCount(0);
  await expect(page.getByText(/¥|库存|路线|餐饮/)).toHaveCount(0);
});

test('登录用户从历史消息确认操作且不展示 actionId', async ({ page }) => {
  await mockAuthenticatedAgent(page, true);
  await page.goto(`/assistant/${sessionId}`);
  const confirm = page.getByRole('button', { name: '确认操作' });
  await expect(confirm).toBeVisible();
  await expect(page.getByText('action-e2e-1')).toHaveCount(0);
  await confirm.click();
  await expect(page.getByText('确认操作已完成')).toBeVisible();
  await expect(confirm).toBeDisabled();
});

test('移动端使用同一类型化确认卡且操作区不溢出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockAuthenticatedAgent(page, true);
  await page.goto(`/assistant/${sessionId}`);

  const card = page.locator('[data-agent-card-kind="plan-card"]');
  await expect(card).toBeVisible();
  await expect(card.getByText('操作确认')).toBeVisible();
  await expect(card.getByRole('button', { name: '确认操作' })).toBeVisible();
  await expect(card.getByRole('button', { name: '拒绝操作' })).toBeVisible();
  await expect(page.getByText('action-e2e-1')).toHaveCount(0);

  const box = await card.boundingBox();
  expect(box).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(390);
});

test('确认结果未知后按历史消息 runId 恢复且不重发 POST', async ({ page }) => {
  const requests = await mockAuthenticatedAgent(page, true, true);
  await page.goto(`/assistant/${sessionId}`);

  const confirm = page.getByRole('button', { name: '确认操作' });
  await expect(confirm).toBeVisible();
  await confirm.click();

  await expect(page.getByText('确认操作已完成')).toBeVisible();
  await expect(confirm).toBeDisabled();
  await expect.poll(requests.confirmationPostCount).toBe(1);
  await expect.poll(requests.messageHistoryQueryCount).toBeGreaterThanOrEqual(2);
  await expect.poll(requests.confirmationRunQueryCount).toBeGreaterThanOrEqual(2);
});
