import { describe, expect, it } from 'vitest';

import { mapAdminAgentRunDetail, mapAdminAgentRunPage } from './mappers';

const summary = {
  runId: 'run_01J4',
  userDisplay: 'u***@example.com',
  status: 'FAILED',
  planId: 'plan_01J4',
  planVersion: 3,
  nodeCount: 1,
  completedNodeCount: 0,
  failedNodeCount: 1,
  startedAt: '2026-08-05T19:20:00+08:00',
  finishedAt: '2026-08-05T19:20:08+08:00',
  durationMs: 8120,
  errorCode: 306003,
  errorSummary: '场次查询暂不可用',
};

describe('管理员 Agent DTO 映射', () => {
  it('保留 B 的脱敏字段并按字符串保存业务 ID', () => {
    const detail = mapAdminAgentRunDetail({
      ...summary,
      nodes: [
        {
          nodeId: 'node-query-shows',
          nodeType: 'CALL_TOOL',
          targetName: 'queryShows',
          status: 'FAILED',
          attemptCount: 1,
          startedAt: summary.startedAt,
          finishedAt: summary.finishedAt,
          durationMs: 3000,
          toolStatus: 'FAILED',
          errorCode: 306003,
          errorSummary: summary.errorSummary,
          recoveryHint: '可修改条件后重新发起',
        },
      ],
      rawToolArgs: 'must not be rendered',
      systemPrompt: 'must not be rendered',
    });

    expect(detail.runId).toBe('run_01J4');
    expect(detail.planId).toBe('plan_01J4');
    expect(detail.nodes[0].nodeId).toBe('node-query-shows');
    expect(detail).not.toHaveProperty('rawToolArgs');
    expect(detail).not.toHaveProperty('systemPrompt');
  });

  it('拒绝缺少分页或节点必填字段的响应', () => {
    expect(() => mapAdminAgentRunPage({ total: 1, page: 1, size: 20, records: [{}] })).toThrow();
    expect(() => mapAdminAgentRunDetail({ ...summary, nodes: [{}] })).toThrow();
  });
});
