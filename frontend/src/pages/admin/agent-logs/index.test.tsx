import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { setupTestEnvironment } from '../../../features/test-utils';
import { ApiError } from '../../../shared/api/ApiError';
import type {
  AdminAgentRunDetail,
  AdminAgentRunPage,
  AdminAgentRunSummary,
} from '../../../modules/admin-agent/types';

const mocks = vi.hoisted(() => {
  const summary: AdminAgentRunSummary = {
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
  return {
    summary,
    detailState: {
      data: null as AdminAgentRunDetail | null,
      error: null as ApiError | null,
      isLoading: false,
      retry: vi.fn(),
    },
    listState: {
      data: { total: 1, page: 1, size: 20, records: [summary] } as AdminAgentRunPage | null,
      error: null as ApiError | null,
      isLoading: false,
      isRefreshing: false,
      retry: vi.fn(),
    },
    searchParams: new URLSearchParams(),
    setSearchParams: vi.fn(),
  };
});

vi.mock('umi', () => ({
  useSearchParams: () => [mocks.searchParams, mocks.setSearchParams],
}));

vi.mock('../../../modules/admin-agent/hooks', () => ({
  useAdminAgentRuns: () => mocks.listState,
  useAdminAgentRunDetail: () => mocks.detailState,
}));

import AdminAgentRunsPage from './index';

setupTestEnvironment();

function openDetail(): void {
  fireEvent.click(screen.getByRole('button', { name: '查看详情' }));
}

describe('AdminAgentRunsPage', () => {
  beforeEach(() => {
    mocks.searchParams = new URLSearchParams();
    mocks.setSearchParams.mockReset();
    mocks.listState.data = { total: 1, page: 1, size: 20, records: [mocks.summary] };
    mocks.listState.error = null;
    mocks.listState.isLoading = false;
    mocks.listState.isRefreshing = false;
    mocks.listState.retry.mockReset();
    mocks.detailState.data = null;
    mocks.detailState.error = null;
    mocks.detailState.isLoading = false;
    mocks.detailState.retry.mockReset();
  });

  it('展示 PR #170 的脱敏列表字段并把筛选写入 URL', () => {
    render(<AdminAgentRunsPage />);

    expect(screen.getByText('run_01J4')).toBeInTheDocument();
    expect(screen.getByText('u***@example.com')).toBeInTheDocument();
    expect(screen.getByText('失败')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('用户关键词'), { target: { value: 'u***' } });
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
    expect(mocks.setSearchParams).toHaveBeenCalled();
  });

  it('拒绝非 OffsetDateTime 和倒序时间范围', () => {
    render(<AdminAgentRunsPage />);

    fireEvent.change(screen.getByLabelText('开始时间'), {
      target: { value: '2026-08-05T20:20:00' },
    });
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
    expect(screen.getByText('开始时间和结束时间必须使用 ISO 8601 格式')).toBeInTheDocument();

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

  it('详情只展示 B 允许的节点摘要', () => {
    mocks.detailState.data = {
      ...mocks.summary,
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
    openDetail();

    expect(screen.getByText('session_01J4')).toBeInTheDocument();
    expect(screen.getByText('node-query-shows')).toBeInTheDocument();
    expect(screen.getByText('queryShows')).toBeInTheDocument();
    expect(screen.queryByText('ToolContext')).not.toBeInTheDocument();
  });

  it('将后端 SUCCESS 状态显示为成功且保留尝试次数列', () => {
    mocks.detailState.data = {
      ...mocks.summary,
      nodes: [
        {
          nodeId: 'node-success',
          nodeType: 'CALL_TOOL',
          targetName: null,
          status: 'SUCCESS',
          attemptCount: 1,
          startedAt: '2026-08-05T19:20:01+08:00',
          finishedAt: '2026-08-05T19:20:04+08:00',
          durationMs: 3000,
          toolStatus: 'SUCCESS',
          errorCode: null,
          errorSummary: null,
          recoveryHint: null,
        },
      ],
    };

    render(<AdminAgentRunsPage />);
    openDetail();

    expect(screen.getByText('成功')).toBeInTheDocument();
    expect(screen.getAllByText('1').some((element) => element.tagName === 'TD')).toBe(true);
    expect(screen.getByText('成功').className).toContain('agent-run-status-tag');
  });

  it('等待定位和空计划使用明确中文占位', () => {
    const waitingRun: AdminAgentRunSummary = {
      ...mocks.summary,
      status: 'WAITING_LOCATION',
      planId: null,
      planVersion: null,
      finishedAt: null,
      durationMs: null,
      errorCode: null,
      errorSummary: null,
    };
    mocks.listState.data = { total: 1, page: 1, size: 20, records: [waitingRun] };
    mocks.detailState.data = {
      ...waitingRun,
      nodes: [
        {
          nodeId: 'node-location',
          nodeType: 'ASK_USER',
          targetName: null,
          status: 'PENDING',
          attemptCount: 0,
          startedAt: null,
          finishedAt: null,
          durationMs: null,
          toolStatus: null,
          errorCode: null,
          errorSummary: null,
          recoveryHint: null,
        },
      ],
    };

    render(<AdminAgentRunsPage />);
    openDetail();

    expect(screen.getAllByText('等待定位').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText('等待定位后生成计划').length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText('vnull')).not.toBeInTheDocument();
    expect(screen.queryByText('undefined')).not.toBeInTheDocument();
    expect(screen.queryByText('NaN')).not.toBeInTheDocument();
  });

  it('未知状态降级显示原始状态且页面继续渲染', () => {
    mocks.listState.data = {
      total: 1,
      page: 1,
      size: 20,
      records: [{ ...mocks.summary, status: 'PAUSED_BY_POLICY' }],
    };

    render(<AdminAgentRunsPage />);

    expect(screen.getByText('未知状态（PAUSED_BY_POLICY）')).toBeInTheDocument();
    expect(screen.getByText('run_01J4')).toBeInTheDocument();
  });

  it('区分首次加载、普通空列表和筛选后空列表', () => {
    mocks.listState.data = null;
    mocks.listState.isLoading = true;
    const { rerender } = render(<AdminAgentRunsPage />);
    expect(screen.getByText('Agent 轨迹加载中')).toBeInTheDocument();

    mocks.listState.isLoading = false;
    mocks.listState.data = { total: 0, page: 1, size: 20, records: [] };
    rerender(<AdminAgentRunsPage />);
    expect(screen.getByText('暂无 Agent 运行记录')).toBeInTheDocument();

    mocks.searchParams = new URLSearchParams('status=FAILED');
    rerender(<AdminAgentRunsPage />);
    expect(screen.getByText('筛选后无数据')).toBeInTheDocument();
  });

  it.each([
    [401, '登录状态已失效，正在返回登录页。'],
    [403, '当前账号没有查看 Agent 轨迹的权限。'],
  ])('列表 HTTP %s 不展示旧管理数据', (status, expectedText) => {
    mocks.listState.data = null;
    mocks.listState.error = new ApiError('safe error', { kind: 'HTTP', status });

    render(<AdminAgentRunsPage />);

    expect(screen.getByText(expectedText)).toBeInTheDocument();
    expect(screen.queryByText('run_01J4')).not.toBeInTheDocument();
  });

  it('5xx 显示 traceId 和重试入口', () => {
    mocks.listState.data = null;
    mocks.listState.error = new ApiError('server error', {
      kind: 'HTTP',
      status: 500,
      traceId: 'trace-list-500',
    });

    render(<AdminAgentRunsPage />);

    expect(screen.getByText('问题编号：trace-list-500')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(mocks.listState.retry).toHaveBeenCalledTimes(1);
  });

  it('刷新失败保留旧列表并支持再次刷新', () => {
    mocks.listState.error = new ApiError('server error', {
      kind: 'HTTP',
      status: 500,
      traceId: 'trace-refresh',
    });

    render(<AdminAgentRunsPage />);

    expect(screen.getByText('run_01J4')).toBeInTheDocument();
    expect(screen.getByText('问题编号：trace-refresh')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '刷新列表' }));
    expect(mocks.listState.retry).toHaveBeenCalledTimes(1);
  });

  it('详情 404 使用失效文案且不显示旧详情', () => {
    mocks.detailState.error = new ApiError('not found', { kind: 'HTTP', status: 404 });

    render(<AdminAgentRunsPage />);
    openDetail();

    expect(screen.getByText('运行记录不存在或已失效。')).toBeInTheDocument();
    expect(screen.queryByText('session_01J4')).not.toBeInTheDocument();
  });

  it('详情 5xx 显示 traceId 并支持重新加载', () => {
    mocks.detailState.error = new ApiError('server error', {
      kind: 'HTTP',
      status: 500,
      traceId: 'trace-detail-500',
    });

    render(<AdminAgentRunsPage />);
    openDetail();

    expect(screen.getByText('问题编号：trace-detail-500')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重新加载' }));
    expect(mocks.detailState.retry).toHaveBeenCalledTimes(1);
  });

  it('翻页写入 URL，窄屏仍渲染筛选和可滚动表格', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    window.dispatchEvent(new Event('resize'));
    mocks.listState.data = { total: 60, page: 1, size: 20, records: [mocks.summary] };

    const { container } = render(<AdminAgentRunsPage />);
    const pageTwo = container.querySelector('.ant-pagination-item-2');
    expect(pageTwo).not.toBeNull();
    fireEvent.click(pageTwo as Element);

    expect(mocks.setSearchParams).toHaveBeenCalled();
    expect(screen.getByLabelText('Agent 轨迹筛选')).toBeInTheDocument();
    expect(screen.getByLabelText('Agent 运行列表')).toBeInTheDocument();
  });

  it('页面不会展示注入的原始工具参数和模型内部内容', () => {
    const unsafeDetail = {
      ...mocks.summary,
      nodes: [],
      rawToolArgs: 'secret-token',
      modelThought: 'private reasoning',
      latitude: 28.2282,
    };
    mocks.detailState.data = unsafeDetail;

    render(<AdminAgentRunsPage />);
    openDetail();

    expect(screen.queryByText('secret-token')).not.toBeInTheDocument();
    expect(screen.queryByText('private reasoning')).not.toBeInTheDocument();
    expect(screen.queryByText('28.2282')).not.toBeInTheDocument();
  });
});
