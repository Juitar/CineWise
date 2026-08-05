import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Pagination,
  Select,
  Skeleton,
  Table,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'umi';

import {
  buildAdminAgentRunSearchParams,
  parseAdminAgentRunQuery,
} from '../../../modules/admin-agent/query';
import {
  resolveAdminAgentErrorState,
  type AdminAgentErrorState,
} from '../../../modules/admin-agent/errors';
import { useAdminAgentRunDetail, useAdminAgentRuns } from '../../../modules/admin-agent/hooks';
import type {
  AdminAgentRunListQuery,
  AdminAgentRunNode,
  AdminAgentRunStatus,
  AdminAgentRunSummary,
} from '../../../modules/admin-agent/types';
import './index.css';

const STATUS_OPTIONS: Array<{ label: string; value: AdminAgentRunStatus }> = [
  { label: '运行中', value: 'RUNNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' },
];

function statusLabel(status: string): string {
  return STATUS_OPTIONS.find((option) => option.value === status)?.label ?? '未知状态';
}

function statusColor(status: string): string {
  switch (status) {
    case 'RUNNING':
      return 'processing';
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
      return 'error';
    case 'CANCELLED':
      return 'warning';
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

function errorMessage(state: AdminAgentErrorState): string {
  return state === 'FORBIDDEN'
    ? '当前账号没有查看 Agent 轨迹的权限'
    : state === 'NOT_FOUND'
      ? '请求的 Agent 运行记录不存在'
      : 'Agent 轨迹暂时无法加载';
}

function RunStatus({ status }: { status: string }) {
  return <Tag color={statusColor(status)}>{statusLabel(status)}</Tag>;
}

const runColumns: ColumnsType<AdminAgentRunSummary> = [
  { dataIndex: 'runId', ellipsis: true, title: '运行 ID' },
  { dataIndex: 'userDisplay', ellipsis: true, title: '用户' },
  { dataIndex: 'status', render: (status: string) => <RunStatus status={status} />, title: '状态' },
  { dataIndex: 'planId', ellipsis: true, title: '计划 ID' },
  { dataIndex: 'planVersion', title: '计划版本' },
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
    render: (value: string | null, run) =>
      value ? `${run.errorCode ? `${run.errorCode} ` : ''}${value}` : '—',
    title: '错误摘要',
  },
];

const nodeColumns: ColumnsType<AdminAgentRunNode> = [
  { dataIndex: 'nodeId', ellipsis: true, title: '节点 ID' },
  { dataIndex: 'nodeType', title: '节点类型' },
  { dataIndex: 'targetName', ellipsis: true, title: '目标' },
  { dataIndex: 'status', render: (status: string) => <RunStatus status={status} />, title: '状态' },
  { dataIndex: 'attemptCount', title: '尝试次数' },
  { dataIndex: 'toolStatus', render: (value: string | null) => value ?? '—', title: '工具状态' },
  { dataIndex: 'durationMs', render: formatDuration, title: '耗时' },
  { dataIndex: 'errorSummary', render: (value: string | null) => value ?? '—', title: '错误摘要' },
  {
    dataIndex: 'recoveryHint',
    render: (value: string | null) => value ?? '—',
    title: '恢复建议',
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
      (startedFrom && Number.isNaN(Date.parse(startedFrom))) ||
      (startedTo && Number.isNaN(Date.parse(startedTo)))
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
        <Alert className="agent-runs-status" message={filterError} showIcon type="warning" />
      ) : null}

      {error ? (
        <Alert
          action={
            listErrorState === 'FORBIDDEN' ? undefined : (
              <Button onClick={retry} size="small">
                重试
              </Button>
            )
          }
          className="agent-runs-status"
          description={error.traceId ? `问题编号：${error.traceId}` : undefined}
          message={listErrorState ? errorMessage(listErrorState) : 'Agent 轨迹加载失败'}
          showIcon
          type="error"
        />
      ) : null}

      {isRefreshing ? <div className="agent-runs-refreshing">正在更新运行记录…</div> : null}

      {isLoading ? (
        <div aria-label="Agent 轨迹加载中" className="agent-runs-loading">
          <Skeleton active paragraph={{ rows: 5 }} />
        </div>
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
      ) : (
        <Empty description={error ? '暂无可展示的运行记录' : '暂无 Agent 运行记录'} />
      )}

      <Drawer
        destroyOnClose
        onClose={() => setSelectedRunId(null)}
        open={selectedRunId !== null}
        title="Agent 运行详情"
        width={820}
      >
        {detailQuery.isLoading ? <Skeleton active paragraph={{ rows: 8 }} /> : null}
        {detailQuery.error ? (
          <Alert
            action={
              detailQuery.error.status === 404 ? undefined : (
                <Button onClick={detailQuery.retry}>重试</Button>
              )
            }
            message={
              resolveAdminAgentErrorState(detailQuery.error) === 'NOT_FOUND'
                ? '运行记录不存在'
                : '运行详情加载失败'
            }
            showIcon
            type="error"
          />
        ) : null}
        {detailQuery.data ? (
          <div className="agent-run-detail">
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="运行 ID">{detailQuery.data.runId}</Descriptions.Item>
              <Descriptions.Item label="用户">{detailQuery.data.userDisplay}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <RunStatus status={detailQuery.data.status} />
              </Descriptions.Item>
              <Descriptions.Item label="计划">
                {detailQuery.data.planId} / v{detailQuery.data.planVersion}
              </Descriptions.Item>
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
                {detailQuery.data.errorSummary ?? '—'}
              </Descriptions.Item>
            </Descriptions>
            <h2>节点轨迹</h2>
            <Table
              columns={nodeColumns}
              dataSource={detailQuery.data.nodes}
              pagination={false}
              rowKey="nodeId"
              scroll={{ x: 1100 }}
              size="small"
            />
          </div>
        ) : null}
      </Drawer>
    </div>
  );
}
