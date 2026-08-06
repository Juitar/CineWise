import { afterEach, describe, expect, it, vi } from 'vitest';

import runCancelled from '../../../../backend/src/test/resources/fixtures/agent/c/run-cancelled.json';
import runCompleted from '../../../../backend/src/test/resources/fixtures/agent/c/run-completed.json';
import sessionCleared from '../../../../backend/src/test/resources/fixtures/agent/c/session-cleared.json';
import sessionCreated from '../../../../backend/src/test/resources/fixtures/agent/c/session-created.json';
import sessionList from '../../../../backend/src/test/resources/fixtures/agent/c/session-list.json';
import sessionMessages from '../../../../backend/src/test/resources/fixtures/agent/c/session-message-history.json';
import sessionsBulkCleared from '../../../../backend/src/test/resources/fixtures/agent/c/sessions-bulk-cleared.json';
import { clearCsrfToken } from '../../shared/api/client';
import {
  cancelAgentRun,
  clearAgentSession,
  clearAllAgentSessions,
  createAgentSession,
  getAgentRun,
  listAgentMessages,
  listAgentSessions,
} from './api';

function response(value: unknown): Response {
  return new Response(JSON.stringify(value), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function csrfResponse(): Response {
  return response({
    code: 0,
    message: 'success',
    traceId: 'trace',
    data: { headerName: 'X-XSRF-TOKEN', token: 'csrf' },
  });
}

afterEach(() => {
  clearCsrfToken();
  vi.unstubAllGlobals();
});

describe('Agent REST API', () => {
  it('直接消费最新后端全部会话和运行夹具', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(response(sessionCreated))
      .mockResolvedValueOnce(response(sessionList))
      .mockResolvedValueOnce(response(sessionMessages))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(response(sessionCleared))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(response(sessionsBulkCleared))
      .mockResolvedValueOnce(response(runCompleted))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(response(runCancelled));
    vi.stubGlobal('fetch', fetchMock);

    await expect(createAgentSession()).resolves.toMatchObject({ sessionId: 'session-example-2' });
    await expect(listAgentSessions()).resolves.toMatchObject({ total: 1 });
    await expect(listAgentMessages('session-example-2')).resolves.toMatchObject({ total: 1 });
    await expect(clearAgentSession('session-example-2')).resolves.toMatchObject({ cleared: true });
    await expect(clearAllAgentSessions()).resolves.toEqual({ clearedCount: 2, skippedCount: 1 });
    await expect(getAgentRun('run-example-1')).resolves.toMatchObject({ lastEventId: '42' });
    await expect(cancelAgentRun('run-example-2')).resolves.toMatchObject({ status: 'CANCELLED' });
  });
});
