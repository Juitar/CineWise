import type {
  AdminAgentRunDetail,
  AdminAgentRunNode,
  AdminAgentRunPage,
  AdminAgentRunSummary,
} from './types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function requiredString(value: unknown, fieldName: string): string {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Agent 轨迹字段 ${fieldName} 无效`);
  }
  return value;
}

function nullableString(value: unknown, fieldName: string): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value !== 'string') {
    throw new Error(`Agent 轨迹字段 ${fieldName} 无效`);
  }
  return value;
}

function integer(value: unknown, fieldName: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value)) {
    throw new Error(`Agent 轨迹字段 ${fieldName} 无效`);
  }
  return value;
}

function nullableInteger(value: unknown, fieldName: string): number | null {
  return value === null || value === undefined ? null : integer(value, fieldName);
}

function isoDateTime(value: unknown, fieldName: string): string {
  const dateTime = requiredString(value, fieldName);
  if (Number.isNaN(Date.parse(dateTime)) || !dateTime.includes('T')) {
    throw new Error(`Agent 轨迹字段 ${fieldName} 无效`);
  }
  return dateTime;
}

function nullableIsoDateTime(value: unknown, fieldName: string): string | null {
  return value === null || value === undefined ? null : isoDateTime(value, fieldName);
}

function mapSummary(value: unknown): AdminAgentRunSummary {
  if (!isRecord(value)) {
    throw new Error('Agent 轨迹列表记录格式无效');
  }
  return {
    completedNodeCount: integer(value.completedNodeCount, 'completedNodeCount'),
    durationMs: nullableInteger(value.durationMs, 'durationMs'),
    errorCode: nullableInteger(value.errorCode, 'errorCode'),
    errorSummary: nullableString(value.errorSummary, 'errorSummary'),
    failedNodeCount: integer(value.failedNodeCount, 'failedNodeCount'),
    finishedAt: nullableIsoDateTime(value.finishedAt, 'finishedAt'),
    nodeCount: integer(value.nodeCount, 'nodeCount'),
    planId: nullableString(value.planId, 'planId'),
    planVersion: nullableInteger(value.planVersion, 'planVersion'),
    runId: requiredString(value.runId, 'runId'),
    sessionId: nullableString(value.sessionId, 'sessionId'),
    startedAt: isoDateTime(value.startedAt, 'startedAt'),
    status: requiredString(value.status, 'status'),
    userDisplay: requiredString(value.userDisplay, 'userDisplay'),
  };
}

function mapNode(value: unknown): AdminAgentRunNode {
  if (!isRecord(value)) {
    throw new Error('Agent 轨迹节点格式无效');
  }
  return {
    attemptCount: integer(value.attemptCount, 'attemptCount'),
    durationMs: nullableInteger(value.durationMs, 'durationMs'),
    errorCode: nullableInteger(value.errorCode, 'errorCode'),
    errorSummary: nullableString(value.errorSummary, 'errorSummary'),
    finishedAt: nullableIsoDateTime(value.finishedAt, 'finishedAt'),
    nodeId: requiredString(value.nodeId, 'nodeId'),
    nodeType: requiredString(value.nodeType, 'nodeType'),
    recoveryHint: nullableString(value.recoveryHint, 'recoveryHint'),
    startedAt: nullableIsoDateTime(value.startedAt, 'startedAt'),
    status: requiredString(value.status, 'status'),
    targetName: nullableString(value.targetName, 'targetName'),
    toolStatus: nullableString(value.toolStatus, 'toolStatus'),
  };
}

/** 在模块边界校验 B 的脱敏 Agent 列表，异常字段不会进入页面渲染。 */
export function mapAdminAgentRunPage(value: unknown): AdminAgentRunPage {
  if (!isRecord(value) || !Array.isArray(value.records)) {
    throw new Error('Agent 轨迹分页格式无效');
  }
  return {
    page: integer(value.page, 'page'),
    records: value.records.map(mapSummary),
    size: integer(value.size, 'size'),
    total: integer(value.total, 'total'),
  };
}

/** 在模块边界校验 B 的脱敏 Agent 详情。 */
export function mapAdminAgentRunDetail(value: unknown): AdminAgentRunDetail {
  if (!isRecord(value) || !Array.isArray(value.nodes)) {
    throw new Error('Agent 轨迹详情格式无效');
  }
  return {
    ...mapSummary(value),
    nodes: value.nodes.map(mapNode),
  };
}
