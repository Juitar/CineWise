export type AdminAgentRunStatus =
  'WAITING_LOCATION' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface AdminAgentRunListQuery {
  status?: AdminAgentRunStatus;
  userKeyword?: string;
  startedFrom?: string;
  startedTo?: string;
  page: number;
  size: number;
}

export interface AdminAgentRunSummary {
  runId: string;
  sessionId: string | null;
  userDisplay: string;
  status: AdminAgentRunStatus | string;
  planId: string | null;
  planVersion: number | null;
  nodeCount: number;
  completedNodeCount: number;
  failedNodeCount: number;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  errorCode: number | null;
  errorSummary: string | null;
}

export interface AdminAgentRunNode {
  nodeId: string;
  nodeType: string;
  targetName: string | null;
  status: string;
  attemptCount: number;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  toolStatus: string | null;
  errorCode: number | null;
  errorSummary: string | null;
  recoveryHint: string | null;
}

export interface AdminAgentRunDetail extends AdminAgentRunSummary {
  nodes: AdminAgentRunNode[];
}

export interface AdminAgentRunPage {
  total: number;
  page: number;
  size: number;
  records: AdminAgentRunSummary[];
}
