import { apiRequest } from '../../shared/api/client';
import {
  parseAgentMessagePage,
  parseAgentRunSnapshot,
  parseAgentSession,
  parseAgentSessionPage,
  parseRunCancelResult,
  parseSessionBulkClearResult,
  parseSessionClearResult,
} from './contract';
import type {
  AgentMessagePage,
  AgentRunCancelResult,
  AgentRunSnapshot,
  AgentSession,
  AgentSessionBulkClearResult,
  AgentSessionClearResult,
  AgentSessionPage,
} from './types';

const basePath = '/api/v1/agent';

export async function createAgentSession(): Promise<AgentSession> {
  return parseAgentSession(await apiRequest<unknown>(`${basePath}/sessions`, { method: 'POST' }));
}

export async function listAgentSessions(page = 1, size = 100): Promise<AgentSessionPage> {
  return parseAgentSessionPage(
    await apiRequest<unknown>(`${basePath}/sessions`, { query: { page, size } }),
  );
}

export async function listAgentMessages(
  sessionId: string,
  page = 1,
  size = 100,
): Promise<AgentMessagePage> {
  return parseAgentMessagePage(
    await apiRequest<unknown>(`${basePath}/sessions/${encodeURIComponent(sessionId)}/messages`, {
      query: { page, size },
    }),
  );
}

export async function clearAgentSession(sessionId: string): Promise<AgentSessionClearResult> {
  return parseSessionClearResult(
    await apiRequest<unknown>(`${basePath}/sessions/${encodeURIComponent(sessionId)}`, {
      method: 'DELETE',
    }),
  );
}

export async function clearAllAgentSessions(): Promise<AgentSessionBulkClearResult> {
  return parseSessionBulkClearResult(
    await apiRequest<unknown>(`${basePath}/sessions`, { method: 'DELETE' }),
  );
}

export async function getAgentRun(runId: string): Promise<AgentRunSnapshot> {
  return parseAgentRunSnapshot(
    await apiRequest<unknown>(`${basePath}/runs/${encodeURIComponent(runId)}`),
  );
}

export async function cancelAgentRun(runId: string): Promise<AgentRunCancelResult> {
  return parseRunCancelResult(
    await apiRequest<unknown>(`${basePath}/runs/${encodeURIComponent(runId)}/cancel`, {
      method: 'POST',
    }),
  );
}
