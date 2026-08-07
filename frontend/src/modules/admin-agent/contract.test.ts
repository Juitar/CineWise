import { describe, expect, it } from 'vitest';

import detailFixture from '../../../../backend/src/test/resources/fixtures/agent/c/admin-agent-run-detail.json';
import pageFixture from '../../../../backend/src/test/resources/fixtures/agent/c/admin-agent-run-page.json';
import { mapAdminAgentRunDetail, mapAdminAgentRunPage } from './mappers';

describe('PR #170 管理端 Agent 轨迹契约', () => {
  it('直接消费 8e72199c 的列表和详情固定夹具', () => {
    const page = mapAdminAgentRunPage(pageFixture.data);
    const detail = mapAdminAgentRunDetail(detailFixture.data);

    expect(page.records[0]).toMatchObject({
      runId: 'run_01J4',
      sessionId: 'session_01J4',
      errorCode: null,
      errorSummary: null,
    });
    expect(detail.nodes[0]).toMatchObject({
      nodeId: 'node-query-shows',
      targetName: null,
      toolStatus: null,
      recoveryHint: null,
    });
    expect(typeof detail.runId).toBe('string');
    expect(typeof detail.sessionId).toBe('string');
  });

  it('接受等待定位运行和 PR #170 声明的全部可空字段', () => {
    const detail = mapAdminAgentRunDetail({
      ...detailFixture.data,
      sessionId: null,
      status: 'WAITING_LOCATION',
      planId: null,
      planVersion: null,
      finishedAt: null,
      durationMs: null,
      nodes: [
        {
          ...detailFixture.data.nodes[0],
          targetName: null,
          startedAt: null,
          finishedAt: null,
          durationMs: null,
          toolStatus: null,
          errorCode: null,
          errorSummary: null,
          recoveryHint: null,
        },
      ],
    });

    expect(detail.status).toBe('WAITING_LOCATION');
    expect(detail.planId).toBeNull();
    expect(detail.planVersion).toBeNull();
    expect(detail.nodes[0].startedAt).toBeNull();
  });

  it('只保留白名单字段并丢弃敏感内容', () => {
    const detail = mapAdminAgentRunDetail({
      ...detailFixture.data,
      rawToolArgs: { token: 'secret-token', latitude: 28.2282 },
      modelThought: 'private reasoning',
      userInput: '完整用户输入',
      nodes: [
        {
          ...detailFixture.data.nodes[0],
          payload: { cookie: 'jwt-cookie' },
        },
      ],
    });

    expect(detail).not.toHaveProperty('rawToolArgs');
    expect(detail).not.toHaveProperty('modelThought');
    expect(detail).not.toHaveProperty('userInput');
    expect(detail.nodes[0]).not.toHaveProperty('payload');
    expect(JSON.stringify(detail)).not.toContain('secret-token');
  });
});
