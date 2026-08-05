import { apiRequest } from '../../shared/api/client';
import type {
  AdminOrderDetailResponse,
  AdminOrderListQuery,
  AdminOrderPageResponse,
} from './types';

/** 查询管理订单分页；身份和 ADMIN 权限仍由 C 的公共安全链与后端复核。 */
export function queryAdminOrders(
  query: AdminOrderListQuery,
  signal?: AbortSignal,
): Promise<AdminOrderPageResponse> {
  return apiRequest<AdminOrderPageResponse>('/api/v1/admin/orders', {
    query: {
      dateFrom: query.dateFrom,
      dateTo: query.dateTo,
      movieId: query.movieId,
      orderNo: query.orderNo,
      page: query.page,
      showId: query.showId,
      size: query.size,
      status: query.status,
      userKeyword: query.userKeyword,
    },
    signal,
  });
}

/** 查询单笔管理订单只读聚合，不提供任何交易状态修改入口。 */
export function queryAdminOrderDetail(
  orderNo: string,
  signal?: AbortSignal,
): Promise<AdminOrderDetailResponse> {
  return apiRequest<AdminOrderDetailResponse>(
    `/api/v1/admin/orders/${encodeURIComponent(orderNo)}`,
    { signal },
  );
}
