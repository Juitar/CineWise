import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import { useAdminAgentRunDetail, useAdminAgentRuns } from './hooks';
import type { AdminAgentRunDetail, AdminAgentRunPage } from './types';

const apiMocks = vi.hoisted(() => ({
  queryAdminAgentRunDetail: vi.fn(),
  queryAdminAgentRuns: vi.fn(),
}));

vi.mock('./api', () => apiMocks);

const summary = {
  runId: 'run_01J4',
  sessionId: 'session_01J4',
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

const page: AdminAgentRunPage = { total: 1, page: 1, size: 20, records: [summary] };
const detail: AdminAgentRunDetail = { ...summary, nodes: [] };

describe('管理员 Agent 查询 Hook', () => {
  beforeEach(() => {
    apiMocks.queryAdminAgentRunDetail.mockReset();
    apiMocks.queryAdminAgentRuns.mockReset();
  });

  it('查询成功后返回脱敏分页快照', async () => {
    apiMocks.queryAdminAgentRuns.mockResolvedValue(page);
    const { result } = renderHook(() => useAdminAgentRuns({ page: 1, size: 20 }));

    await waitFor(() => expect(result.current.data?.records[0].runId).toBe('run_01J4'));
    expect(result.current.isLoading).toBe(false);
  });

  it('详情 404 保留稳定错误，不伪造空详情', async () => {
    apiMocks.queryAdminAgentRunDetail.mockRejectedValue(
      new ApiError('not found', { kind: 'HTTP', status: 404 }),
    );
    const { result } = renderHook(() => useAdminAgentRunDetail(detail.runId));

    await waitFor(() => expect(result.current.error?.status).toBe(404));
    expect(result.current.data).toBeNull();
  });

  it('关闭详情后丢弃迟到响应并清除上一条数据', async () => {
    let resolveDetail: ((value: AdminAgentRunDetail) => void) | undefined;
    apiMocks.queryAdminAgentRunDetail.mockReturnValue(
      new Promise<AdminAgentRunDetail>((resolve) => {
        resolveDetail = resolve;
      }),
    );
    const { result, rerender } = renderHook(
      ({ runId }: { runId: string | null }) => useAdminAgentRunDetail(runId),
      { initialProps: { runId: detail.runId as string | null } },
    );

    rerender({ runId: null });
    expect(result.current.data).toBeNull();
    expect(result.current.isLoading).toBe(false);
    resolveDetail?.(detail);
    await waitFor(() => expect(result.current.data).toBeNull());
  });

  it('列表权限失效时清除已加载的管理数据', async () => {
    apiMocks.queryAdminAgentRuns
      .mockResolvedValueOnce(page)
      .mockRejectedValueOnce(new ApiError('forbidden', { kind: 'HTTP', status: 403 }));
    const { result, rerender } = renderHook(
      ({ pageNumber }: { pageNumber: number }) => useAdminAgentRuns({ page: pageNumber, size: 20 }),
      { initialProps: { pageNumber: 1 } },
    );

    await waitFor(() => expect(result.current.data?.records).toHaveLength(1));
    rerender({ pageNumber: 2 });
    await waitFor(() => expect(result.current.error?.status).toBe(403));
    expect(result.current.data).toBeNull();
  });
});
