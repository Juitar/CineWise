import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import detailSuccess from '../../../../backend/src/test/resources/fixtures/ticketing/c/admin-order-detail-success.json';
import pageSuccess from '../../../../backend/src/test/resources/fixtures/ticketing/c/admin-order-page-success.json';
import directoryUnavailable from '../../../../backend/src/test/resources/fixtures/ticketing/c/admin-user-directory-unavailable-error.json';
import queryTooBroad from '../../../../backend/src/test/resources/fixtures/ticketing/c/admin-user-query-too-broad-error.json';
import { queryAdminOrderDetail, queryAdminOrders } from './api';

function response(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    headers: { 'Content-Type': 'application/json' },
    status,
  });
}

describe('管理订单 API 契约', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => vi.restoreAllMocks());

  it('按后端白名单参数查询分页并保留字符串 ID 与金额', async () => {
    fetchMock.mockResolvedValueOnce(response(pageSuccess));

    const page = await queryAdminOrders({
      dateFrom: '2026-08-01',
      dateTo: '2026-08-10',
      page: 1,
      size: 20,
      status: 'REFUNDED',
      userKeyword: 'r@example',
    });

    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/v1/admin/orders?dateFrom=2026-08-01&dateTo=2026-08-10&page=1&size=20&status=REFUNDED&userKeyword=r%40example',
    );
    expect(page.records[0].orderId).toBe('3002');
    expect(page.records[0].totalAmount).toBe('68.00');
  });

  it('按编码后的订单号查询详情且不包含敏感交易字段', async () => {
    fetchMock.mockResolvedValueOnce(response(detailSuccess));

    const detail = await queryAdminOrderDetail('CW/REFUNDED-1');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/orders/CW%2FREFUNDED-1');
    expect(detail.summary.orderNo).toBe('CW-REFUNDED-1');
    expect(detail).not.toHaveProperty('qrPayload');
  });

  it.each([
    [queryTooBroad, 400, 201010],
    [directoryUnavailable, 503, 301002],
  ])('保留稳定错误码和 traceId', async (payload, status, code) => {
    fetchMock.mockResolvedValueOnce(response(payload, status));

    const request = queryAdminOrders({ page: 1, size: 20, userKeyword: 'keyword' });

    await expect(request).rejects.toMatchObject({
      code,
      traceId: payload.traceId,
    });
  });
});
