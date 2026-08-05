import { describe, expect, it } from 'vitest';

import { buildAdminAgentRunSearchParams, parseAdminAgentRunQuery } from './query';

describe('管理员 Agent 查询条件', () => {
  it('从 URL 恢复状态、时间和分页条件', () => {
    const query = parseAdminAgentRunQuery(
      new URLSearchParams(
        'status=FAILED&userKeyword=u%2A%2A%2A&startedFrom=2026-08-05T19%3A20%3A00%2B08%3A00&startedTo=2026-08-05T20%3A20%3A00%2B08%3A00&page=2&size=50',
      ),
    );

    expect(query).toEqual({
      page: 2,
      size: 50,
      startedFrom: '2026-08-05T19:20:00+08:00',
      startedTo: '2026-08-05T20:20:00+08:00',
      status: 'FAILED',
      userKeyword: 'u***',
    });
  });

  it('生成不包含 runId 或敏感上下文的可分享 URL', () => {
    const params = buildAdminAgentRunSearchParams({
      page: 1,
      size: 20,
      startedFrom: '2026-08-05T19:20:00+08:00',
      startedTo: '2026-08-05T20:20:00+08:00',
      status: 'FAILED',
      userKeyword: 'u***',
    });

    expect(params.toString()).toBe(
      'status=FAILED&userKeyword=u***&startedFrom=2026-08-05T19%3A20%3A00%2B08%3A00&startedTo=2026-08-05T20%3A20%3A00%2B08%3A00',
    );
    expect(params.has('runId')).toBe(false);
  });
});
