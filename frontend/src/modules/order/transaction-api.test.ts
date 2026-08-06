import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearCsrfToken } from '../../shared/api/client';
import {
  createRefund,
  getAlternativeShows,
  getElectronicTicket,
  getOrder,
  getOrders,
  payOrder,
} from './api';
import alternativeShowsPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/alternative-shows-success.json';
import electronicTicketPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/electronic-ticket-success.json';
import orderDetailPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/order-detail-success.json';
import orderPagePayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/order-page-success.json';
import paymentPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/payment-success.json';
import refundPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/refund-success.json';

function response(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const csrfPayload = {
  code: 0,
  message: 'success',
  data: { token: 'csrf-token', headerName: 'X-XSRF-TOKEN' },
  traceId: 'csrf-trace',
};

describe('订单第二批 REST 契约', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    clearCsrfToken();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => vi.restoreAllMocks());

  it('解析订单分页、电子票和替代场次固定夹具', async () => {
    fetchMock
      .mockResolvedValueOnce(response(orderPagePayload))
      .mockResolvedValueOnce(response(electronicTicketPayload))
      .mockResolvedValueOnce(response(alternativeShowsPayload));

    const orders = await getOrders({ page: 1, size: 10 });
    const ticket = await getElectronicTicket('2084194700000000001');
    const alternatives = await getAlternativeShows('CW2084194500000000001');

    expect(orders.records[0].orderId).toBe('2084194500000000001');
    expect(orders.records[0]).toMatchObject({
      cinemaId: '2084194399128586242',
      movieId: '2084194398004512769',
      showStartTime: '2026-08-03T20:00:00+08:00',
    });
    expect(ticket.status).toBe('VALID');
    expect(alternatives.shows[0].movieId).toBe('2084194398004512769');
  });

  it('订单详情查询解析服务端权威场次上下文', async () => {
    fetchMock.mockResolvedValueOnce(response(orderDetailPayload));

    const order = await getOrder('CW2084194500000000001');

    expect(order).toMatchObject({
      cinemaId: '2084194399128586242',
      movieId: '2084194398004512769',
    });
    expect(order.showStartTime).toBe('2026-08-03T20:00:00+08:00');
  });

  it('支付请求不包含密码或业务请求体，只发送稳定幂等键', async () => {
    fetchMock
      .mockResolvedValueOnce(response(csrfPayload))
      .mockResolvedValueOnce(response(paymentPayload));
    const payment = await payOrder('CW2084194500000000001', 'payment-key');

    expect(payment.paymentStatus).toBe('SUCCESS');
    const request = fetchMock.mock.calls[1][1] as RequestInit;
    expect(request.body).toBeUndefined();
    expect(new Headers(request.headers).get('Idempotency-Key')).toBe('payment-key');
    expect(JSON.stringify(request)).not.toContain('password');
  });

  it('传统页面退款省略 actionId 并解析服务端权威结果', async () => {
    fetchMock
      .mockResolvedValueOnce(response(csrfPayload))
      .mockResolvedValueOnce(response(refundPayload));
    const refund = await createRefund(
      'CW2084194500000000001',
      { clientRequestId: 'refund-request', refundReason: '行程变化' },
      'refund-key',
    );

    expect(refund.refundStatus).toBe('SUCCESS');
    const request = fetchMock.mock.calls[1][1] as RequestInit;
    expect(request.body).toBe(
      JSON.stringify({ clientRequestId: 'refund-request', refundReason: '行程变化' }),
    );
    expect(request.body).not.toContain('actionId');
  });

  it.each([
    {
      status: 403,
      code: 201009,
      message: '安全校验已失效，请重新操作',
      traceId: 'refund-trace-403',
      refreshCsrf: true,
    },
    {
      status: 401,
      code: 201006,
      message: '登录状态已失效',
      traceId: 'refund-trace-401',
      refreshCsrf: false,
    },
    {
      status: 409,
      code: 204003,
      message: '当前退款状态不允许重复申请',
      traceId: 'refund-trace-409',
      refreshCsrf: false,
    },
  ])(
    '退款请求收到 $status/$code 且省略 data 时保留公共 ApiError',
    async ({ status, code, message, traceId, refreshCsrf }) => {
      fetchMock
        .mockResolvedValueOnce(response(csrfPayload))
        .mockResolvedValueOnce(response({ code, message, traceId }, status));
      if (refreshCsrf) {
        fetchMock.mockResolvedValueOnce(
          response({
            code: 0,
            message: 'success',
            data: { token: 'csrf-refreshed', headerName: 'X-XSRF-TOKEN' },
            traceId: 'csrf-refreshed-trace',
          }),
        );
      }

      await expect(
        createRefund(
          'CW2084194500000000001',
          { clientRequestId: 'refund-error-request', refundReason: '行程变化' },
          'refund-error-key',
        ),
      ).rejects.toMatchObject({
        name: 'ApiError',
        kind: 'HTTP',
        status,
        code,
        message,
        traceId,
      });

      expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('/refunds'))).toHaveLength(
        1,
      );
    },
  );
});
