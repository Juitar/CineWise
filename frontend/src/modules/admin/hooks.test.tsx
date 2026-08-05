import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { AdminOrderDetailResponse, AdminOrderPageResponse, AdminOrderStatus } from './types';
import { useAdminOrderDetail, useAdminOrders } from './hooks';

const adminApiMocks = vi.hoisted(() => ({
  queryAdminOrderDetail: vi.fn(),
  queryAdminOrders: vi.fn(),
}));

vi.mock('./api', () => adminApiMocks);

const page: AdminOrderPageResponse = {
  page: 1,
  records: [
    {
      cinemaId: '602',
      createdAt: '2026-08-04T08:00:00+08:00',
      emailMasked: 'r***@example.com',
      expireTime: '2026-08-04T08:15:00+08:00',
      movieId: '502',
      orderId: '3002',
      orderNo: 'CW-REFUNDED-1',
      orderStatus: 'REFUNDED',
      paymentStatus: 'SUCCESS',
      refundStatus: 'SUCCESS',
      showId: '2002',
      showStartTime: '2026-08-06T09:00:00+08:00',
      stateVersion: 3,
      ticketCount: 1,
      ticketStatus: 'REFUNDED',
      totalAmount: '68.00',
      unitPrice: '68.00',
      updatedAt: '2026-08-04T09:00:00+08:00',
      userId: '1002',
    },
  ],
  size: 20,
  total: 1,
};

const detail: AdminOrderDetailResponse = {
  seats: [],
  summary: page.records[0],
};

describe('管理订单查询 Hook', () => {
  beforeEach(() => {
    adminApiMocks.queryAdminOrderDetail.mockReset();
    adminApiMocks.queryAdminOrders.mockReset();
  });

  it('查询成功后返回真实分页快照', async () => {
    adminApiMocks.queryAdminOrders.mockResolvedValue(page);
    const { result } = renderHook(() => useAdminOrders({ page: 1, size: 20 }));

    await waitFor(() => expect(result.current.data?.records[0].orderNo).toBe('CW-REFUNDED-1'));
    expect(result.current.isLoading).toBe(false);
  });

  it('刷新失败保留已有列表并允许按原条件手动重试', async () => {
    adminApiMocks.queryAdminOrders
      .mockResolvedValueOnce(page)
      .mockRejectedValueOnce(
        new ApiError('directory unavailable', { kind: 'HTTP', status: 503, code: 301002 }),
      );
    const { result } = renderHook(() => useAdminOrders({ page: 1, size: 20 }));
    await waitFor(() => expect(result.current.data).not.toBeNull());

    act(() => result.current.retry());
    await waitFor(() => expect(result.current.error?.code).toBe(301002));
    expect(result.current.data?.records).toHaveLength(1);
  });

  it('筛选切换后迟到响应不能覆盖新查询', async () => {
    let resolveFirst: ((value: AdminOrderPageResponse) => void) | undefined;
    const secondPage = { ...page, records: [{ ...page.records[0], orderNo: 'CW-NEW' }] };
    adminApiMocks.queryAdminOrders
      .mockImplementationOnce(
        () =>
          new Promise<AdminOrderPageResponse>((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockResolvedValueOnce(secondPage);
    const initialProps: { status: AdminOrderStatus } = { status: 'PAID' };
    const { result, rerender } = renderHook(
      ({ status }: { status: AdminOrderStatus }) => useAdminOrders({ page: 1, size: 20, status }),
      { initialProps },
    );

    rerender({ status: 'REFUNDED' });
    await waitFor(() => expect(result.current.data?.records[0].orderNo).toBe('CW-NEW'));
    await act(async () => {
      resolveFirst?.(page);
      await Promise.resolve();
    });
    expect(result.current.data?.records[0].orderNo).toBe('CW-NEW');
  });

  it('详情响应统一补齐可空字段且切换订单会拒绝迟到响应', async () => {
    let resolveFirst: ((value: AdminOrderDetailResponse) => void) | undefined;
    const secondDetail = {
      ...detail,
      summary: { ...detail.summary, orderNo: 'CW-SECOND' },
    };
    adminApiMocks.queryAdminOrderDetail
      .mockImplementationOnce(
        () =>
          new Promise<AdminOrderDetailResponse>((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockResolvedValueOnce(secondDetail);
    const { result, rerender } = renderHook(({ orderNo }) => useAdminOrderDetail(orderNo), {
      initialProps: { orderNo: 'CW-FIRST' as string | null },
    });

    rerender({ orderNo: 'CW-SECOND' });
    await waitFor(() => expect(result.current.data?.summary.orderNo).toBe('CW-SECOND'));
    expect(result.current.data?.cancelledTime).toBeNull();
    await act(async () => {
      resolveFirst?.(detail);
      await Promise.resolve();
    });
    expect(result.current.data?.summary.orderNo).toBe('CW-SECOND');
  });
});
