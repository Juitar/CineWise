import { Button, Descriptions, Drawer, Input, Pagination, Select, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'umi';

import {
  buildAdminAgentRunSearchParams,
  isAdminAgentIsoDateTime,
  parseAdminAgentRunQuery,
} from '../../../modules/admin-agent/query';
import { resolveAdminAgentErrorState } from '../../../modules/admin-agent/errors';
import { useAdminAgentRunDetail, useAdminAgentRuns } from '../../../modules/admin-agent/hooks';
import type {
  AdminAgentRunListQuery,
  AdminAgentRunNode,
  AdminAgentRunStatus,
  AdminAgentRunSummary,
} from '../../../modules/admin-agent/types';
import {
  PageEmpty,
  PageError,
  PageForbidden,
  PageLoading,
  PageNotFound,
  PageRefreshErrorNotice,
  PageRefreshingNotice,
} from '../../../shared/components/page-state';
import './index.css';

const STATUS_OPTIONS: Array<{ label: string; value: AdminAgentRunStatus }> = [
  { label: '等待定位', value: 'WAITING_LOCATION' },
  { label: '运行中', value: 'RUNNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' },
];

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    CANCELLED: '已取消',
    COMPLETED: '已完成',
    FAILED: '失败',
    PENDING: '待执行',
    RUNNING: '运行中',
    SUCCESS: '成功',
    SKIPPED: '已跳过',
    SUCCEEDED: '成功',
    WAITING_LOCATION: '等待定位',
  };
  return labels[status] ?? `未知状态（${status.slice(0, 50)}）`;
}

function statusColor(status: string): string {
  switch (status) {
    case 'WAITING_LOCATION':
      return 'gold';
    case 'RUNNING':
      return 'processing';
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
      return 'error';
    case 'CANCELLED':
      return 'warning';
    case 'SUCCESS':
    case 'SUCCEEDED':
      return 'success';
    default:
      return 'default';
  }
}

function formatDateTime(value: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '时间待更新' : date.toLocaleString('zh-CN');
}

function formatDuration(durationMs: number | null): string {
  if (durationMs === null) return '—';
  return durationMs >= 1000 ? `${(durationMs / 1000).toFixed(2)} 秒` : `${durationMs} 毫秒`;
}

function formatNullable(value: string | number | null): string {
  return value === null ? '—' : String(value);
}

function formatPlan(run: Pick<AdminAgentRunSummary, 'planId' | 'planVersion' | 'status'>): string {
  if (run.planId === null && run.planVersion === null) {
    return run.status === 'WAITING_LOCATION' ? '等待定位后生成计划' : '尚未生成计划';
  }
  if (run.planId === null) {
    return `计划版本 v${run.planVersion}`;
  }
  return run.planVersion === null ? run.planId : `${run.planId} / v${run.planVersion}`;
}

function formatError(errorCode: number | null, errorSummary: string | null): string {
  if (errorCode === null && errorSummary === null) return '—';
  if (errorCode === null) return errorSummary ?? '—';
  return errorSummary === null ? String(errorCode) : `${errorCode} ${errorSummary}`;
}

function RunStatus({ status }: { status: string }) {
  return (
    <Tag className="agent-run-status-tag" color={statusColor(status)} title={statusLabel(status)}>
      {statusLabel(status)}
    </Tag>
  );
}

const runColumns: ColumnsType<AdminAgentRunSummary> = [
  { dataIndex: 'runId', ellipsis: true, title: '运行 ID' },
  { dataIndex: 'userDisplay', ellipsis: true, title: '用户' },
  { dataIndex: 'status', render: (status: string) => <RunStatus status={status} />, title: '状态' },
  {
    dataIndex: 'planId',
    ellipsis: true,
    render: (_value: string | null, run) => formatPlan(run),
    title: '计划',
  },
  {
    dataIndex: 'nodeCount',
    render: (_value: number, run) =>
      `${run.completedNodeCount}/${run.nodeCount}，失败 ${run.failedNodeCount}`,
    title: '节点完成度',
  },
  { dataIndex: 'startedAt', render: formatDateTime, title: '开始时间' },
  { dataIndex: 'durationMs', render: formatDuration, title: '耗时' },
  {
    dataIndex: 'errorSummary',
    render: (_value: string | null, run) => formatError(run.errorCode, run.errorSummary),
    title: '错误摘要',
  },
];

const nodeColumns: ColumnsType<AdminAgentRunNode> = [
  { dataIndex: 'nodeId', ellipsis: true, title: '节点 ID', width: 180 },
  { dataIndex: 'nodeType', title: '节点类型', width: 120 },
  { dataIndex: 'targetName', ellipsis: true, render: formatNullable, title: '目标', width: 140 },
  {
    dataIndex: 'status',
    render: (status: string) => <RunStatus status={status} />,
    title: '状态',
    width: 150,
  },
  { dataIndex: 'attemptCount', title: '尝试次数', width: 90 },
  {
    dataIndex: 'toolStatus',
    render: (value: string | null) => value ?? '—',
    title: '工具状态',
    width: 120,
  },
  { dataIndex: 'durationMs', render: formatDuration, title: '耗时', width: 100 },
  {
    dataIndex: 'errorSummary',
    render: (_value: string | null, node) => formatError(node.errorCode, node.errorSummary),
    title: '错误摘要',
    width: 220,
  },
  {
    dataIndex: 'recoveryHint',
    render: (value: string | null) => value ?? '—',
    title: '恢复建议',
    width: 180,
  },
];

/** 管理员 Agent 脱敏轨迹页；页面只组合 admin-agent 模块，不直接发送网络请求。 */
export default function AdminAgentRunsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const searchParamsKey = searchParams.toString();
  const query = useMemo(
    () => parseAdminAgentRunQuery(new URLSearchParams(searchParamsKey)),
    [searchParamsKey],
  );
  const { data, error, isLoading, isRefreshing, retry } = useAdminAgentRuns(query);
  const [keywordDraft, setKeywordDraft] = useState(query.userKeyword ?? '');
  const [startedFromDraft, setStartedFromDraft] = useState(query.startedFrom ?? '');
  const [startedToDraft, setStartedToDraft] = useState(query.startedTo ?? '');
  const [filterError, setFilterError] = useState<string | null>(null);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const detailQuery = useAdminAgentRunDetail(selectedRunId);

  useEffect(() => {
    setKeywordDraft(query.userKeyword ?? '');
    setStartedFromDraft(query.startedFrom ?? '');
    setStartedToDraft(query.startedTo ?? '');
  }, [query.startedFrom, query.startedTo, query.userKeyword]);

  const updateQuery = (patch: Partial<AdminAgentRunListQuery>) => {
    setSearchParams(buildAdminAgentRunSearchParams({ ...query, ...patch }));
  };

  const handleSearch = () => {
    const startedFrom = startedFromDraft.trim();
    const startedTo = startedToDraft.trim();
    if (
      (startedFrom && !isAdminAgentIsoDateTime(startedFrom)) ||
      (startedTo && !isAdminAgentIsoDateTime(startedTo))
    ) {
      setFilterError('开始时间和结束时间必须使用 ISO 8601 格式');
      return;
    }
    if (startedFrom && startedTo && Date.parse(startedTo) < Date.parse(startedFrom)) {
      setFilterError('结束时间不得早于开始时间');
      return;
    }
    setFilterError(null);
    updateQuery({
      page: 1,
      startedFrom: startedFrom || undefined,
      startedTo: startedTo || undefined,
      userKeyword: keywordDraft.trim() || undefined,
    });
  };

  const handleReset = () => {
    setKeywordDraft('');
    setStartedFromDraft('');
    setStartedToDraft('');
    setFilterError(null);
    setSearchParams(buildAdminAgentRunSearchParams({ page: 1, size: query.size }));
  };

  const listErrorState = error ? resolveAdminAgentErrorState(error) : null;
  const detailErrorState = detailQuery.error
    ? resolveAdminAgentErrorState(detailQuery.error)
    : null;
  const hasActiveFilters = Boolean(
    query.status || query.userKeyword || query.startedFrom || query.startedTo,
  );
  const hasBlockingListError = listErrorState === 'UNAUTHORIZED' || listErrorState === 'FORBIDDEN';

  return (
    <div className="agent-runs-page">
      <nav className="admin-breadcrumb" aria-label="面包屑">
        <span>管理端</span>
        <span aria-hidden="true">&gt;</span>
        <span className="current">Agent 轨迹</span>
      </nav>
      <header className="agent-runs-heading">
        <div>
          <h1 className="page-title">Agent 轨迹</h1>
          <p>仅展示后端返回的脱敏运行摘要，不展示思维过程和原始工具参数。</p>
        </div>
        <Button loading={isRefreshing} onClick={retry}>
          刷新列表
        </Button>
      </header>

      <section className="agent-runs-filters" aria-label="Agent 轨迹筛选">
        <Input
          aria-label="用户关键词"
          maxLength={100}
          onChange={(event) => setKeywordDraft(event.target.value)}
          placeholder="脱敏用户标识"
          value={keywordDraft}
        />
        <Select
          allowClear
          aria-label="运行状态"
          onChange={(status: AdminAgentRunStatus | undefined) => updateQuery({ page: 1, status })}
          options={STATUS_OPTIONS}
          placeholder="全部状态"
          value={query.status}
        />
        <Input
          aria-label="开始时间"
          onChange={(event) => setStartedFromDraft(event.target.value)}
          placeholder="开始时间 ISO 8601"
          value={startedFromDraft}
        />
        <Input
          aria-label="结束时间"
          onChange={(event) => setStartedToDraft(event.target.value)}
          placeholder="结束时间 ISO 8601"
          value={startedToDraft}
        />
        <div className="agent-runs-filter-actions">
          <Button onClick={handleReset}>重置</Button>
          <Button onClick={handleSearch} type="primary">
            查询
          </Button>
        </div>
      </section>

      {filterError ? (
        <p className="agent-runs-filter-error" role="alert">
          {filterError}
        </p>
      ) : null}

      {isRefreshing && data ? (
        <PageRefreshingNotice description="正在更新运行记录，当前列表仍可查看。" />
      ) : null}

      {error && data && !hasBlockingListError ? (
        <PageRefreshErrorNotice
          description="运行记录刷新失败，当前列表仍可查看。"
          onRetry={retry}
          traceId={error.traceId}
        />
      ) : null}

      {isLoading ? (
        <PageLoading label="Agent 轨迹加载中" />
      ) : error && (data === null || hasBlockingListError) ? (
        listErrorState === 'FORBIDDEN' ? (
          <PageForbidden description="当前账号没有查看 Agent 轨迹的权限。" />
        ) : (
          <PageError
            description={
              listErrorState === 'UNAUTHORIZED'
                ? '登录状态已失效，正在返回登录页。'
                : 'Agent 轨迹暂时无法加载，请稍后重试。'
            }
            onRetry={listErrorState === 'UNAUTHORIZED' ? undefined : retry}
            traceId={error.traceId}
          />
        )
      ) : data && data.records.length > 0 ? (
        <section aria-label="Agent 运行列表" className="agent-runs-table-wrapper">
          <Table
            columns={[
              ...runColumns,
              {
                key: 'detail',
                render: (_value: unknown, run: AdminAgentRunSummary) => (
                  <Button onClick={() => setSelectedRunId(run.runId)} type="link">
                    查看详情
                  </Button>
                ),
                title: '操作',
              },
            ]}
            dataSource={data.records}
            pagination={false}
            rowKey="runId"
            scroll={{ x: 1300 }}
          />
          <Pagination
            current={data.page}
            onChange={(page, size) => updateQuery({ page, size })}
            pageSize={data.size}
            showSizeChanger
            total={data.total}
          />
        </section>
      ) : data ? (
        <PageEmpty
          actionLabel={hasActiveFilters ? '重置筛选' : undefined}
          description={
            hasActiveFilters ? '当前筛选条件下没有 Agent 运行记录。' : '当前还没有 Agent 运行记录。'
          }
          onAction={hasActiveFilters ? handleReset : undefined}
          title={hasActiveFilters ? '筛选后无数据' : '暂无 Agent 运行记录'}
        />
      ) : (
        <PageLoading label="Agent 轨迹加载中" />
      )}

      <Drawer
        destroyOnClose
        extra={
          <Button disabled={detailQuery.isLoading} onClick={detailQuery.retry}>
            重新加载
          </Button>
        }
        onClose={() => setSelectedRunId(null)}
        open={selectedRunId !== null}
        title="Agent 运行详情"
        width="min(820px, 100vw)"
      >
        {detailQuery.isLoading ? <PageLoading label="运行详情加载中" /> : null}
        {detailQuery.error ? (
          detailErrorState === 'FORBIDDEN' ? (
            <PageForbidden description="当前账号没有查看此运行详情的权限。" />
          ) : detailErrorState === 'NOT_FOUND' ? (
            <PageNotFound
              actionLabel="返回列表"
              description="运行记录不存在或已失效。"
              onAction={() => setSelectedRunId(null)}
            />
          ) : (
            <PageError
              description={
                detailErrorState === 'UNAUTHORIZED'
                  ? '登录状态已失效，正在返回登录页。'
                  : '运行详情加载失败，请稍后重新加载。'
              }
              onRetry={detailErrorState === 'UNAUTHORIZED' ? undefined : detailQuery.retry}
              traceId={detailQuery.error.traceId}
            />
          )
        ) : null}
        {detailQuery.data ? (
          <div className="agent-run-detail">
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="运行 ID">{detailQuery.data.runId}</Descriptions.Item>
              <Descriptions.Item label="会话 ID">
                {formatNullable(detailQuery.data.sessionId)}
              </Descriptions.Item>
              <Descriptions.Item label="用户">{detailQuery.data.userDisplay}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <RunStatus status={detailQuery.data.status} />
              </Descriptions.Item>
              <Descriptions.Item label="计划">{formatPlan(detailQuery.data)}</Descriptions.Item>
              <Descriptions.Item label="开始时间">
                {formatDateTime(detailQuery.data.startedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="结束时间">
                {formatDateTime(detailQuery.data.finishedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="耗时">
                {formatDuration(detailQuery.data.durationMs)}
              </Descriptions.Item>
              <Descriptions.Item label="错误摘要">
                {formatError(detailQuery.data.errorCode, detailQuery.data.errorSummary)}
              </Descriptions.Item>
            </Descriptions>
            <h2>节点轨迹</h2>
            <Table
              columns={nodeColumns}
              dataSource={detailQuery.data.nodes}
              pagination={false}
              rowKey="nodeId"
              scroll={{ x: 1300 }}
              size="small"
            />
          </div>
        ) : null}
      </Drawer>
    </div>
  );
}
