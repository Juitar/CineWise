import { ApiError } from '../../shared/api/ApiError';
import type { AdminOrderState } from '../../features/admin-order/types';

/** 未知异常统一失败关闭，不把程序错误解释成空订单列表。 */
export function toAdminApiError(error: unknown): ApiError {
  return error instanceof ApiError
    ? error
    : new ApiError('管理订单查询失败', { kind: 'INVALID_RESPONSE' });
}

/** 页面按稳定状态和错误码分支，禁止解析后端错误文案。 */
export function resolveAdminOrderErrorState(error: ApiError): AdminOrderState {
  if (error.status === 403 || error.code === 100403) {
    return 'FORBIDDEN';
  }
  if (error.code === 201010) {
    return 'QUERY_TOO_BROAD';
  }
  if (error.code === 301002) {
    return 'DIRECTORY_UNAVAILABLE';
  }
  return 'GENERAL_ERROR';
}

export interface AdminDetailErrorPresentation {
  canRetry: boolean;
  message: string;
}

/** 详情 403/404 属于明确结果，只有服务不可用或网络异常允许手动重查。 */
export function resolveAdminDetailError(error: ApiError): AdminDetailErrorPresentation {
  if (error.status === 403 || error.code === 100403) {
    return { canRetry: false, message: '无权限访问订单详情' };
  }
  if (error.status === 404 || error.code === 205001) {
    return { canRetry: false, message: '订单不存在或无权访问' };
  }
  return { canRetry: true, message: '获取订单详情失败，请稍后重试' };
}
