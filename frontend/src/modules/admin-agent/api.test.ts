import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { queryAdminAgentRunDetail, queryAdminAgentRuns } from './api';

function response(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    headers: { 'Content-Type': 'application/json' },
    status,
  });
}

const pagePayload = {
  code: 0,
  message: 'success',
  traceId: 'trace-agent-page',
  data: { total: 1, page: 1, size: 20, records: [] },
};

describe('管理员 Agent API 契约', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => vi.restoreAllMocks());

  it('按 B 的查询字段发送列表请求', async () => {
    fetchMock.mockResolvedValueOnce(response(pagePayload));

    await queryAdminAgentRuns({
      page: 1,
      size: 20,
      startedFrom: '2026-08-05T19:20:00+08:00',
      startedTo: '2026-08-05T20:20:00+08:00',
      status: 'FAILED',
      userKeyword: 'u***',
    });

    expect(fetchMock.mock.calls[0][0]).toContain('/api/v1/admin/agent-runs?');
    expect(fetchMock.mock.calls[0][0]).toContain('status=FAILED');
    expect(fetchMock.mock.calls[0][0]).toContain('startedFrom=2026-08-05T19%3A20%3A00%2B08%3A00');
    expect(fetchMock.mock.calls[0][0]).toContain('startedTo=2026-08-05T20%3A20%3A00%2B08%3A00');
    expect(fetchMock.mock.calls[0][0]).toContain('userKeyword=u***');
    expect(fetchMock.mock.calls[0][0]).toContain('page=1');
    expect(fetchMock.mock.calls[0][0]).toContain('size=20');
  });

  it('空筛选值不会进入列表请求', async () => {
    fetchMock.mockResolvedValueOnce(response(pagePayload));

    await queryAdminAgentRuns({
      page: 1,
      size: 20,
      startedFrom: ' ',
      startedTo: '',
      userKeyword: '   ',
    });

    const url = new URL(fetchMock.mock.calls[0][0] as string, 'https://cinewise.test');
    expect(url.searchParams.has('status')).toBe(false);
    expect(url.searchParams.has('userKeyword')).toBe(false);
    expect(url.searchParams.has('startedFrom')).toBe(false);
    expect(url.searchParams.has('startedTo')).toBe(false);
    expect(url.searchParams.get('page')).toBe('1');
    expect(url.searchParams.get('size')).toBe('20');
  });

  it('按字符串编码 runId 查询详情并保留错误码', async () => {
    fetchMock.mockResolvedValueOnce(
      response({ code: 0, message: 'success', traceId: 'trace-agent-detail', data: {} }),
    );

    await queryAdminAgentRunDetail('run/01J4');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/agent-runs/run%2F01J4');
  });

  it.each([
    [401, 201001, 'trace-unauthorized'],
    [403, 201007, 'trace-forbidden'],
    [404, 206005, 'trace-not-found'],
    [500, 300001, 'trace-server-error'],
  ])('保留 HTTP %s、业务码和 traceId', async (status, code, traceId) => {
    fetchMock.mockResolvedValueOnce(response({ code, message: 'safe error', traceId }, status));

    await expect(queryAdminAgentRuns({ page: 1, size: 20 })).rejects.toMatchObject({
      code,
      status,
      traceId,
    });
  });
});
