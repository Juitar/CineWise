import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { setupTestEnvironment } from '../../../features/test-utils';

const mocks = vi.hoisted(() => ({
  detail: null as Record<string, unknown> | null,
  list: {
    data: {
      total: 1,
      page: 1,
      size: 20,
      records: [
        {
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
        },
      ],
    },
    error: null,
    isLoading: false,
    isRefreshing: false,
    retry: vi.fn(),
  },
  searchParams: new URLSearchParams(),
  setSearchParams: vi.fn(),
}));

vi.mock('umi', () => ({
  useSearchParams: () => [mocks.searchParams, mocks.setSearchParams],
}));

vi.mock('../../../modules/admin-agent/hooks', () => ({
  useAdminAgentRuns: () => mocks.list,
  useAdminAgentRunDetail: () => ({
    data: mocks.detail,
    error: null,
    isLoading: false,
    retry: vi.fn(),
  }),
}));

import AdminAgentRunsPage from './index';

setupTestEnvironment();

describe('AdminAgentRunsPage', () => {
  beforeEach(() => {
    mocks.detail = null;
    mocks.setSearchParams.mockReset();
  });

  it('展示 B 契约中的脱敏列表字段并把筛选写入 URL', () => {
    render(<AdminAgentRunsPage />);

    expect(screen.getByText('run_01J4')).toBeInTheDocument();
    expect(screen.getByText('u***@example.com')).toBeInTheDocument();
    expect(screen.getByText('失败')).toBeInTheDocument();
    expect(screen.queryByText('must not be rendered')).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('用户关键词'), {
      target: { value: 'u***' },
    });
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
    expect(mocks.setSearchParams).toHaveBeenCalled();
  });

  it('结束时间早于开始时间时不发送查询', () => {
    render(<AdminAgentRunsPage />);

    fireEvent.change(screen.getByLabelText('开始时间'), {
      target: { value: '2026-08-05T20:20:00+08:00' },
    });
    fireEvent.change(screen.getByLabelText('结束时间'), {
      target: { value: '2026-08-05T19:20:00+08:00' },
    });
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));

    expect(screen.getByText('结束时间不得早于开始时间')).toBeInTheDocument();
    expect(mocks.setSearchParams).not.toHaveBeenCalled();
  });

  it('点击详情时只展示 B 允许的节点摘要', () => {
    mocks.detail = {
      ...mocks.list.data.records[0],
      nodes: [
        {
          nodeId: 'node-query-shows',
          nodeType: 'CALL_TOOL',
          targetName: 'queryShows',
          status: 'FAILED',
          attemptCount: 1,
          startedAt: '2026-08-05T19:20:01+08:00',
          finishedAt: '2026-08-05T19:20:04+08:00',
          durationMs: 3000,
          toolStatus: 'FAILED',
          errorCode: 306003,
          errorSummary: '场次查询暂不可用',
          recoveryHint: '可修改条件后重新发起',
        },
      ],
    };

    render(<AdminAgentRunsPage />);
    fireEvent.click(screen.getByRole('button', { name: '查看详情' }));

    expect(screen.getByText('node-query-shows')).toBeInTheDocument();
    expect(screen.getByText('queryShows')).toBeInTheDocument();
    expect(screen.queryByText('ToolContext')).not.toBeInTheDocument();
  });
});
