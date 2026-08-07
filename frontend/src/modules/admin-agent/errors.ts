import { ApiError } from '../../shared/api/ApiError';

/** 未知响应统一转换为安全的管理轨迹错误，不把异常文本展示给管理员。 */
export function toAdminAgentApiError(error: unknown): ApiError {
  return error instanceof ApiError
    ? error
    : new ApiError('Agent 轨迹查询失败', { kind: 'INVALID_RESPONSE' });
}

export type AdminAgentErrorState = 'UNAUTHORIZED' | 'FORBIDDEN' | 'NOT_FOUND' | 'GENERAL_ERROR';

/** 页面按 HTTP 状态和业务码分支，不依赖后端 message。 */
export function resolveAdminAgentErrorState(error: ApiError): AdminAgentErrorState {
  if (error.status === 401) {
    return 'UNAUTHORIZED';
  }
  if (error.status === 403 || error.code === 201007) {
    return 'FORBIDDEN';
  }
  if (error.status === 404) {
    return 'NOT_FOUND';
  }
  return 'GENERAL_ERROR';
}
