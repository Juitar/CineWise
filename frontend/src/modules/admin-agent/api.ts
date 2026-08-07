import { apiRequest } from '../../shared/api/client';
import type { AdminAgentRunDetail, AdminAgentRunListQuery, AdminAgentRunPage } from './types';

/** 查询管理员可见的脱敏 Agent 运行列表；权限由 RequireAdmin 和后端共同确认。 */
export function queryAdminAgentRuns(
  query: AdminAgentRunListQuery,
  signal?: AbortSignal,
): Promise<AdminAgentRunPage> {
  const startedFrom = query.startedFrom?.trim();
  const startedTo = query.startedTo?.trim();
  const userKeyword = query.userKeyword?.trim();
  return apiRequest<AdminAgentRunPage>('/api/v1/admin/agent-runs', {
    query: {
      page: query.page,
      size: query.size,
      startedFrom: startedFrom || undefined,
      startedTo: startedTo || undefined,
      status: query.status,
      userKeyword: userKeyword || undefined,
    },
    signal,
  });
}

/** 查询单次脱敏 Agent 运行详情；runId 只按字符串路径参数传递。 */
export function queryAdminAgentRunDetail(
  runId: string,
  signal?: AbortSignal,
): Promise<AdminAgentRunDetail> {
  return apiRequest<AdminAgentRunDetail>(`/api/v1/admin/agent-runs/${encodeURIComponent(runId)}`, {
    signal,
  });
}
