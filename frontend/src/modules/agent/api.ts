import { apiRequest } from '../../shared/api/client';
import {
  parseAgentMessagePage,
  parseAgentActionConfirmationResult,
  parseAgentRunSnapshot,
  parseAgentSession,
  parseAgentSessionPage,
  parseRunCancelResult,
  parseSessionBulkClearResult,
  parseSessionClearResult,
} from './contract';
import type {
  AgentMessagePage,
  AgentActionConfirmationResult,
  AgentRunCancelResult,
  AgentRunSnapshot,
  AgentSession,
  AgentSessionBulkClearResult,
  AgentSessionClearResult,
  AgentSessionPage,
} from './types';

const basePath = '/api/v1/agent';

export async function resolveBrowserCity(longitude: number, latitude: number): Promise<string> {
  // 浏览器可能返回 13 位以上小数；市级定位只需米级精度，避免触发后端 @Digits 校验。
  const normalizedLongitude = Number(longitude.toFixed(6));
  const normalizedLatitude = Number(latitude.toFixed(6));
  const response = await apiRequest<{ city?: unknown }>(`${basePath}/location/city`, {
    method: 'POST',
    body: { longitude: normalizedLongitude, latitude: normalizedLatitude },
    // 城市确认只是辅助输入；第三方服务不可用时尽快回到用户可手输的追问卡。
    timeoutMs: 8_000,
  });
  if (typeof response.city !== 'string' || !response.city.trim()) {
    throw new Error('当前位置无法识别城市，请手动输入城市。');
  }
  return response.city.trim();
}

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
export async function confirmAgentAction(
  actionId: string,
  confirmed: boolean,
): Promise<AgentActionConfirmationResult> {
  return parseAgentActionConfirmationResult(
    await apiRequest<unknown>(`${basePath}/actions/${encodeURIComponent(actionId)}/confirm`, {
      method: 'POST',
      body: { confirmed },
    }),
  );
}
