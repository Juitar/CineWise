import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { isResultUnknownError, recoverCreatedOrder } from './recovery';
import { createOrder } from './api';
import { ApiError } from '../../shared/api/ApiError';
import { clearCsrfToken } from '../../shared/api/client';
import createOrderSuccessPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/create-order-success.json';
import idempotencyMismatchErrorPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/idempotency-mismatch-error.json';
import unauthenticatedErrorPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/unauthenticated-error.json';

function createApiResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const csrfTokenPayload = {
  code: 0,
  message: 'success',
  data: {
    token: 'test-csrf-token',
    headerName: 'X-XSRF-TOKEN',
  },
  traceId: 'csrf-trace-id',
};

describe('订单模块 RESULT_UNKNOWN 错误分类与幂等读补偿单元测试', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    clearCsrfToken();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('isResultUnknownError 能正确分辨需要触发补偿情况与普通业务异常', () => {
    // 1. 网络中断 / 超时 (isResultUnknown: true)
    const netErr = new ApiError('网络错误', { kind: 'NETWORK', isResultUnknown: true });
    expect(isResultUnknownError(netErr)).toBe(true);

    // 2. 网关 502/504
    const badGatewayErr = new ApiError('Bad Gateway', { kind: 'HTTP', status: 502, code: 0 });
    expect(isResultUnknownError(badGatewayErr)).toBe(true);
    const gatewayTimeoutErr = new ApiError('Gateway Timeout', {
      kind: 'HTTP',
      status: 504,
      code: 0,
    });
    expect(isResultUnknownError(gatewayTimeoutErr)).toBe(true);

    // 3. 业务错 / 普通 HTTP 状态码绝不得算作 RESULT_UNKNOWN
    const authErr = new ApiError('401 未登录', { kind: 'HTTP', status: 401, code: 100401 });
    expect(isResultUnknownError(authErr)).toBe(false);

    const conflictErr = new ApiError('座位不可锁定', { kind: 'HTTP', status: 409, code: 204001 });
    expect(isResultUnknownError(conflictErr)).toBe(false);

    const validationErr = new ApiError('参数异常', { kind: 'HTTP', status: 422, code: 100422 });
    expect(isResultUnknownError(validationErr)).toBe(false);
  });

  it('createOrder 成功时能解析标准 create-order-success 夹具数据', async () => {
    fetchMock.mockResolvedValueOnce(createApiResponse(csrfTokenPayload));
    fetchMock.mockResolvedValueOnce(createApiResponse(createOrderSuccessPayload));

    const res = await createOrder(
      {
        showId: '2084194401305432066',
        seatIds: ['2084194402305432067'],
        clientRequestId: 'req-uuid-1',
      },
      'idempotency-key-1',
    );
    expect(res.orderId).toBe('2084194500000000001');
    expect(res.orderNo).toBe('CW2084194500000000001');
    expect(res.status).toBe('PENDING_PAYMENT');
  });

  it('createOrder 遇到 205005 幂等重放异常或 401 时抛出规范 ApiError', async () => {
    fetchMock.mockResolvedValueOnce(createApiResponse(csrfTokenPayload));
    fetchMock.mockResolvedValueOnce(createApiResponse(idempotencyMismatchErrorPayload, 409));
    await expect(
      createOrder({ showId: '1', seatIds: ['1'], clientRequestId: 'req' }, 'key'),
    ).rejects.toThrowError(ApiError);

    fetchMock.mockResolvedValueOnce(createApiResponse(unauthenticatedErrorPayload, 401));
    await expect(
      createOrder({ showId: '1', seatIds: ['1'], clientRequestId: 'req' }, 'key'),
    ).rejects.toThrowError(ApiError);
  });

  it('recoverCreatedOrder 能够经由原 clientRequestId 查询补偿，404 时安全返回 null 且不重新发 POST', async () => {
    fetchMock.mockResolvedValueOnce(createApiResponse(createOrderSuccessPayload));
    const recovered = await recoverCreatedOrder('req-uuid-1');
    expect(recovered?.orderNo).toBe('CW2084194500000000001');
    expect(fetchMock).toHaveBeenCalledTimes(1);

    // 404 返回 null
    fetchMock.mockResolvedValueOnce(createApiResponse({ code: 100404, message: 'Not found' }, 404));
    const notFound = await recoverCreatedOrder('not-existing');
    expect(notFound).toBeNull();
  });
});
