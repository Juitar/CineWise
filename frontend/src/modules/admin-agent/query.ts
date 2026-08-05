import type { AdminAgentRunListQuery, AdminAgentRunStatus } from './types';

export const DEFAULT_ADMIN_AGENT_PAGE = 1;
export const DEFAULT_ADMIN_AGENT_SIZE = 20;
export const MAX_ADMIN_AGENT_SIZE = 100;
const STATUS_VALUES: readonly AdminAgentRunStatus[] = [
  'RUNNING',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
];

function parsePositiveInteger(
  raw: string | null,
  fallback: number,
  maximum = Number.MAX_SAFE_INTEGER,
) {
  if (!raw || !/^\d+$/.test(raw)) {
    return fallback;
  }
  const value = Number(raw);
  return Number.isSafeInteger(value) && value >= 1 && value <= maximum ? value : fallback;
}

function parseOptionalText(raw: string | null): string | undefined {
  const value = raw?.trim();
  return value ? value : undefined;
}

/** 从 URL 恢复管理员 Agent 列表条件；非法分页和状态使用安全默认值。 */
export function parseAdminAgentRunQuery(searchParams: URLSearchParams): AdminAgentRunListQuery {
  const rawStatus = searchParams.get('status');
  const status = STATUS_VALUES.includes(rawStatus as AdminAgentRunStatus)
    ? (rawStatus as AdminAgentRunStatus)
    : undefined;
  return {
    page: parsePositiveInteger(searchParams.get('page'), DEFAULT_ADMIN_AGENT_PAGE),
    size: parsePositiveInteger(
      searchParams.get('size'),
      DEFAULT_ADMIN_AGENT_SIZE,
      MAX_ADMIN_AGENT_SIZE,
    ),
    startedFrom: parseOptionalText(searchParams.get('startedFrom')),
    startedTo: parseOptionalText(searchParams.get('startedTo')),
    status,
    userKeyword: parseOptionalText(searchParams.get('userKeyword')),
  };
}

/** 生成可刷新和可分享的 Agent 列表 URL，不写入任何凭据或敏感上下文。 */
export function buildAdminAgentRunSearchParams(query: AdminAgentRunListQuery): URLSearchParams {
  const searchParams = new URLSearchParams();
  if (query.status) searchParams.set('status', query.status);
  if (query.userKeyword) searchParams.set('userKeyword', query.userKeyword);
  if (query.startedFrom) searchParams.set('startedFrom', query.startedFrom);
  if (query.startedTo) searchParams.set('startedTo', query.startedTo);
  if (query.page !== DEFAULT_ADMIN_AGENT_PAGE) searchParams.set('page', String(query.page));
  if (query.size !== DEFAULT_ADMIN_AGENT_SIZE) searchParams.set('size', String(query.size));
  return searchParams;
}
