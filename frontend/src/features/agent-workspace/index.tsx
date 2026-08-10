import { Alert, Button, Collapse, Empty, Input, Spin, Tag, Tooltip } from 'antd';
import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'umi';

import { takePendingAgentDraft } from '../../modules/agent/entryDraft';
import { type AgentDisplayItem, type AgentPlanDisplay } from '../../modules/agent/projection';
import type { AgentSession } from '../../modules/agent/types';
import { useAgentWorkspace } from '../../modules/agent/useAgentWorkspace';
import { safePosterUrl } from '../../modules/content/poster';
import { useCinemaList } from '../../modules/content/useCinemaList';
import { useMovieDetail } from '../../modules/content/useMovieDetail';
import { useMovieList } from '../../modules/content/useMovieList';
import { CinemaIcon, FilmIcon, RobotIcon } from '../../shared/components/icons/layout-icons';
import { AgentDisplayItemView } from './cards';
import { RecommendationPlanDetail } from './recommendation-plan-detail';
import './index.css';

interface SessionListProps {
  activeSessionId: string;
  sessions: readonly AgentSession[];
  activeSessionBusy: boolean;
  deletingSessionId: string | null;
  onSelect(sessionId: string): void;
  onDelete(sessionId: string): void;
}

function SessionList({
  activeSessionId,
  sessions,
  activeSessionBusy,
  deletingSessionId,
  onSelect,
  onDelete,
}: SessionListProps) {
  if (sessions.length === 0) return <Empty description="暂无历史会话" />;
  return (
    <div className="agent-session-list" role="list" aria-label="Agent 会话">
      {sessions.map((session) => {
        const current = session.sessionId === activeSessionId;
        const deleting = session.sessionId === deletingSessionId;
        const disabled = deleting || (current && activeSessionBusy);
        const label = session.summary || '新会话';
        return (
          <div
            role="listitem"
            className={`agent-session-item${current ? ' is-active' : ''}`}
            key={session.sessionId}
          >
            <button
              type="button"
              className="agent-session-select"
              onClick={() => onSelect(session.sessionId)}
            >
              <span>{label}</span>
              <small>{session.status}</small>
            </button>
            <Tooltip
              title={
                current && activeSessionBusy
                  ? '当前会话正在运行，结束后才能删除'
                  : '删除此会话'
              }
            >
              <Button
                aria-label={`删除会话：${label}`}
                className="agent-session-delete"
                danger
                disabled={disabled}
                icon={<WorkspaceIcon kind="delete" />}
                loading={deleting}
                onClick={() => onDelete(session.sessionId)}
                size="small"
                type="text"
              />
            </Tooltip>
          </div>
        );
      })}
    </div>
  );
}

const STATUS_TEXT = {
  IDLE: '等待输入',
  CONNECTING: '正在连接',
  STREAMING: '正在处理',
  WAITING_LOCATION: '等待位置',
  COMPLETED: '已完成',
  FAILED: '未完成',
  RESULT_UNKNOWN: '结果待确认',
  CANCELLED: '已取消',
} as const;

const PLAN_TYPE_TEXT: Readonly<Record<string, string>> = {
  COMPREHENSIVE: '综合推荐',
  LOW_PRICE: '低价优先',
  TIME_FIRST: '时间优先',
  EARLY_TIME: '时间较早',
};

interface AgentWorkspaceProps {
  sessionId: string;
  variant?: 'debug' | 'recommendations';
  businessContent?: React.ReactNode;
  mobileBusinessDetail?: boolean;
  initialSelectedPlanIndex?: number;
  planDetail?: boolean;
}

interface SelectedPlanRef {
  itemKey: string;
  index: number;
}

function taskTitle(pathname: string): string {
  if (/\/shows\/[^/]+\/seats$/.test(pathname)) return '选座';
  if (/\/orders\/confirm$/.test(pathname)) return '确认订单';
  if (/\/payments\/[^/]+\/result$/.test(pathname)) return '支付结果';
  if (/\/payments\/[^/]+$/.test(pathname)) return '支付';
  if (/\/orders\/[^/]+$/.test(pathname)) return '订单详情';
  if (/\/tickets\/[^/]+$/.test(pathname)) return '电子票';
  if (/\/shows$/.test(pathname)) return '选择场次';
  return '购票任务';
}

function AgentTaskShell({
  children,
  sessionId,
  title,
  workspaceBase,
}: {
  children: React.ReactNode;
  sessionId: string;
  title: string;
  workspaceBase: string;
}) {
  return (
    <section className="agent-task-shell" aria-label={title}>
      <header className="agent-task-header">
        <Link className="agent-task-back" to={`${workspaceBase}/${encodeURIComponent(sessionId)}`}>
          返回方案
        </Link>
        <nav className="agent-task-steps" aria-label="购票步骤">
          <span className={title === '选择场次' ? 'is-current' : undefined}>场次</span>
          <span className={title === '选座' ? 'is-current' : undefined}>选座</span>
          <span className={title === '确认订单' ? 'is-current' : undefined}>确认订单</span>
          <span className={title === '支付' || title === '支付结果' ? 'is-current' : undefined}>支付</span>
        </nav>
      </header>
      {children}
    </section>
  );
}

function formatPlanTime(value: string): string {
  const time = new Date(value);
  return Number.isNaN(time.getTime())
    ? '场次时间待确认'
    : new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(time);
}

function formatPlanReason(value: string): string {
  const labels: Record<string, string> = {
    COMPREHENSIVE: '综合条件更均衡',
    LOW_PRICE: '当前价格更低',
    EARLY_TIME: '开场时间更早',
    TIME_FIRST: '开场时间更合适',
  };
  const key = Object.keys(labels).find((candidate) => value.includes(candidate));
  return key ? labels[key] : value;
}

function WorkspaceIcon({ kind }: { kind: 'add' | 'clear' | 'delete' | 'history' }) {
  if (kind === 'add') {
    return (
      <svg aria-hidden="true" viewBox="0 0 24 24">
        <path d="M12 5v14M5 12h14" />
      </svg>
    );
  }
  if (kind === 'clear') {
    return (
      <svg aria-hidden="true" viewBox="0 0 24 24">
        <path d="M4 7h16M10 11v6M14 11v6M9 7l1-3h4l1 3M7 7l1 13h8l1-13" />
      </svg>
    );
  }
  if (kind === 'delete') {
    return (
      <svg aria-hidden="true" viewBox="0 0 24 24">
        <path d="M5 7h14M10 11v6M14 11v6M9 7l1-3h4l1 3M7 7l1 13h10l1-13" />
      </svg>
    );
  }
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24">
      <path d="M4 5h16v14H4zM8 9h8M8 13h5" />
    </svg>
  );
}

function AgentDiscoveryPane({ sessionId }: { sessionId: string }) {
  const movies = useMovieList({ page: 1, size: 5 });
  const cinemas = useCinemaList({ location: '430100', page: 1, size: 3 });
  const workspaceBase = `/recommendations/${encodeURIComponent(sessionId)}`;

  return (
    <section className="agent-discovery-pane" aria-label="首页浏览区">
      <div className="agent-discovery-hero">
        <span>妙语观影工作台</span>
        <h1>先浏览，也可以直接说出你的观影需求</h1>
        <p>
          右侧助手会把你的条件整理成可购方案；选片、选档期、选座和确认订单都留在这个工作区完成。
        </p>
      </div>
      <section className="agent-discovery-section" aria-labelledby="agent-discovery-movies">
        <div className="agent-discovery-section-head">
          <div>
            <FilmIcon size={19} />
            <h2 id="agent-discovery-movies">正在热映</h2>
          </div>
          <Link to="/movies">全部影片</Link>
        </div>
        <div className="agent-discovery-movie-grid">
          {movies.data?.records.map((movie) => {
            const poster = safePosterUrl(movie.posterUrl);
            return (
              <Link
                className="agent-discovery-movie"
                key={movie.movieId}
                to={`${workspaceBase}/movies/${encodeURIComponent(movie.movieId)}`}
              >
                {poster ? (
                  <img alt="" src={poster} />
                ) : (
                  <div className="agent-discovery-poster-fallback">
                    <FilmIcon size={24} />
                  </div>
                )}
                <strong>{movie.title}</strong>
                <span>{movie.genres.join(' / ') || '类型待更新'}</span>
              </Link>
            );
          })}
          {movies.isLoading && <Spin />}
          {!movies.isLoading && !movies.data?.records.length && <Empty description="暂无影片" />}
        </div>
      </section>
      <section className="agent-discovery-section" aria-labelledby="agent-discovery-cinemas">
        <div className="agent-discovery-section-head">
          <div>
            <CinemaIcon size={19} />
            <h2 id="agent-discovery-cinemas">长沙影院</h2>
          </div>
          <Link to="/cinemas">全部影院</Link>
        </div>
        <div className="agent-discovery-cinema-grid">
          {cinemas.data?.records.map((cinema) => (
            <Link
              className="agent-discovery-cinema"
              key={cinema.cinemaId}
              to={`/cinemas/${encodeURIComponent(cinema.cinemaId)}`}
            >
              <strong>{cinema.name}</strong>
              <span>{cinema.area || '长沙'}</span>
            </Link>
          ))}
          {cinemas.isLoading && <Spin />}
        </div>
      </section>
    </section>
  );
}

function RecommendationPlanOption({
  index,
  onSelect,
  plan,
  selected,
}: {
  index: number;
  onSelect(index: number): void;
  plan: AgentPlanDisplay;
  selected: boolean;
}) {
  const movie = useMovieDetail(plan.movieId);
  const posterUrl = safePosterUrl(movie.data?.posterUrl ?? null);
  const expired = plan.expired || Date.parse(plan.expiresAt) <= Date.now();

  return (
    <div role="listitem">
      <button
        aria-pressed={selected}
        className={`agent-recommendation-option${selected ? ' is-selected' : ''}`}
        onClick={() => onSelect(index)}
        type="button"
      >
        <div className="agent-recommendation-option-poster">
          {posterUrl ? <img alt="" src={posterUrl} /> : <FilmIcon size={22} />}
        </div>
        <div className="agent-recommendation-option-content">
          <div className="agent-recommendation-option-heading">
            <span>{PLAN_TYPE_TEXT[plan.planType] ?? plan.planType}</span>
            <strong>
              {plan.currency === 'CNY' ? '¥' : `${plan.currency} `}
              {plan.price}
            </strong>
          </div>
          <h2>{movie.data?.title ?? plan.movieName}</h2>
          <p className="agent-recommendation-cinema">{plan.cinemaName}</p>
          <dl className="agent-recommendation-details">
            <div>
              <dt>场次</dt>
              <dd>{formatPlanTime(plan.startTime)}</dd>
            </div>
            {plan.rating !== null && (
              <div>
                <dt>评分</dt>
                <dd>{plan.rating}</dd>
              </div>
            )}
          </dl>
          {!!plan.reasons.length && (
            <ul className="agent-recommendation-reasons">
              {plan.reasons.map((reason) => (
                <li key={reason}>{formatPlanReason(reason)}</li>
              ))}
            </ul>
          )}
          <div className="agent-recommendation-meta">
            <span>{plan.purchaseEligible ? '可查看方案详情' : '当前不可直接购买'}</span>
            <span>以进入选座页后的最新座位状态为准</span>
          </div>
          {(expired || !plan.purchaseEligible) && (
            <span className="agent-recommendation-unavailable">
              {expired ? '方案已过期' : '当前不可购'}
            </span>
          )}
          {selected && <span className="agent-recommendation-selected">当前选择</span>}
        </div>
      </button>
    </div>
  );
}

function RecommendationPlanPane({
  item,
  onSelect,
  selectedIndex,
}: {
  item: AgentDisplayItem | null;
  onSelect(index: number): void;
  selectedIndex: number | null;
}) {
  return (
    <section className="agent-recommendation-pane" aria-labelledby="agent-recommendation-title">
      <header className="agent-recommendation-header">
        <div>
          <span className="agent-recommendation-eyebrow">AI 推荐方案工作区</span>
          <h1 id="agent-recommendation-title">选择适合你的观影方案</h1>
        </div>
        <Tag color="blue">最多 3 个</Tag>
      </header>

      {!item?.plans?.length ? (
        <Empty description="告诉右侧 Agent 你的观影需求后，真实推荐方案会显示在这里" />
      ) : (
        <div className="agent-recommendation-list" role="list" aria-label="推荐方案">
          {item.plans.map((plan: AgentPlanDisplay, index: number) => {
            return (
              <RecommendationPlanOption
                index={index}
                key={`${item.key}:${plan.showId}:${index}`}
                onSelect={onSelect}
                plan={plan}
                selected={selectedIndex === index}
              />
            );
          })}
        </div>
      )}

      {item?.relaxationSuggestion && (
        <p className="agent-recommendation-relaxation">可调整条件：{item.relaxationSuggestion}</p>
      )}
      <p className="agent-recommendation-note">
        方案来自 Agent 实时结果。进入选座前仍以票务服务的最新场次状态为准。
      </p>
    </section>
  );
}

/** Agent 工作区视图；桌面和移动布局共享同一个 useAgentWorkspace 状态。 */
export function AgentWorkspace({
  sessionId,
  variant = 'debug',
  businessContent,
  mobileBusinessDetail = false,
  initialSelectedPlanIndex,
  planDetail = false,
}: AgentWorkspaceProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const [selectedPlanRef, setSelectedPlanRef] = useState<SelectedPlanRef | null>(null);
  const [deletingSessionId, setDeletingSessionId] = useState<string | null>(null);
  const workspace = useAgentWorkspace(sessionId);
  const busy = ['CONNECTING', 'STREAMING', 'WAITING_LOCATION', 'RESULT_UNKNOWN'].includes(
    workspace.projection.status,
  );
  const routeBase = variant === 'recommendations' ? '/recommendations' : '/assistant';
  const latestPlanIndex = workspace.projection.items.reduce(
    (latestIndex, item, index) =>
      item.kind === 'plan-card' && !item.confirmation ? index : latestIndex,
    -1,
  );
  const latestPlanItem = latestPlanIndex >= 0 ? workspace.projection.items[latestPlanIndex] : null;
  const selectedIndex =
    latestPlanItem && selectedPlanRef?.itemKey === latestPlanItem.key
      ? selectedPlanRef.index
      : null;
  const detailPlan =
    selectedIndex === null || latestPlanItem?.plans === undefined
      ? null
      : latestPlanItem.plans[selectedIndex] ?? null;

  const selectSession = (nextSessionId: string) => {
    workspace.stopActiveStream();
    navigate(`${routeBase}/${encodeURIComponent(nextSessionId)}`);
  };

  const createSession = async () => {
    const created = await workspace.createNewSession();
    if (created) selectSession(created.sessionId);
  };

  const clearCurrentSession = async () => {
    const cleared = await workspace.clearCurrent();
    if (!cleared) return;

    const created = await workspace.createNewSession();
    workspace.stopActiveStream();
    navigate(
      created ? `${routeBase}/${encodeURIComponent(created.sessionId)}` : routeBase,
      { replace: true },
    );
  };

  const clearAllSessions = async () => {
    const cleared = await workspace.clearAll();
    if (!cleared) return;

    const created = await workspace.createNewSession();
    workspace.stopActiveStream();
    navigate(
      created ? `${routeBase}/${encodeURIComponent(created.sessionId)}` : routeBase,
      { replace: true },
    );
  };

  const deleteSession = async (targetSessionId: string) => {
    if (targetSessionId === sessionId && busy) return;
    setDeletingSessionId(targetSessionId);
    try {
      const deleted = await workspace.deleteSession(targetSessionId);
      if (!deleted || targetSessionId !== sessionId) return;

      const created = await workspace.createNewSession();
      workspace.stopActiveStream();
      navigate(
        created ? `${routeBase}/${encodeURIComponent(created.sessionId)}` : routeBase,
        { replace: true },
      );
    } finally {
      setDeletingSessionId(null);
    }
  };

  const send = () => {
    const content = draft;
    setDraft('');
    const restoreDraft = () => {
      setDraft((current) => (current.length > 0 ? current : content));
    };
    void workspace.submit(content).then((sent) => {
      if (!sent) restoreDraft();
    }, restoreDraft);
  };

  const selectPlan = (index: number) => {
    if (!latestPlanItem?.plans?.[index]) return;
    setSelectedPlanRef({ itemKey: latestPlanItem.key, index });
  };

  const openPlanDetail = (index: number) => {
    selectPlan(index);
    navigate(`${routeBase}/${encodeURIComponent(sessionId)}/plan?plan=${index}`);
  };

  useEffect(() => {
    setSelectedPlanRef(null);
  }, [sessionId, latestPlanItem?.key]);

  useEffect(() => {
    if (
      initialSelectedPlanIndex === undefined
      || !latestPlanItem?.plans?.[initialSelectedPlanIndex]
    ) {
      return;
    }
    setSelectedPlanRef({ itemKey: latestPlanItem.key, index: initialSelectedPlanIndex });
  }, [initialSelectedPlanIndex, latestPlanItem?.key, latestPlanItem?.plans]);

  useEffect(() => {
    if (workspace.loadStatus !== 'ready') return;
    const pendingDraft = takePendingAgentDraft();
    if (pendingDraft) void workspace.submit(pendingDraft);
  }, [workspace.loadStatus, workspace.submit]);

  const sessionHistory = (
    <div className="agent-workspace-sidebar-content">
      <SessionList
        activeSessionId={sessionId}
        sessions={workspace.sessions}
        activeSessionBusy={busy}
        deletingSessionId={deletingSessionId}
        onSelect={selectSession}
        onDelete={(targetSessionId) => void deleteSession(targetSessionId)}
      />
    </div>
  );

  return (
    <section
      className={`agent-workspace agent-workspace--${variant}${mobileBusinessDetail ? ' agent-workspace--mobile-business-detail' : ''}`}
      aria-label="妙语 Agent 工作区"
    >
      {variant === 'debug' && <aside className="agent-workspace-sidebar">{sessionHistory}</aside>}
      {variant === 'recommendations' && (
        <div className="agent-workspace-business-pane">
          {businessContent ? (
            <AgentTaskShell
              sessionId={sessionId}
              title={taskTitle(location.pathname)}
              workspaceBase={routeBase}
            >
              {businessContent}
            </AgentTaskShell>
          ) : planDetail && detailPlan ? (
            <RecommendationPlanDetail
              plan={detailPlan}
              sessionId={sessionId}
              workspaceBase={routeBase}
            />
          ) : latestPlanItem ? (
            <RecommendationPlanPane
              item={latestPlanItem}
              onSelect={openPlanDetail}
              selectedIndex={selectedIndex}
            />
          ) : (
            <AgentDiscoveryPane sessionId={sessionId} />
          )}
        </div>
      )}
      <div className="agent-workspace-main">
        <header className="agent-workspace-header">
          <div className="agent-workspace-brand">
            <span className="agent-workspace-brand-icon">
              <RobotIcon size={18} />
            </span>
            <div>
              {variant === 'recommendations' ? (
                <h2>妙语 AI 观影助手</h2>
              ) : (
                <h1>妙语 AI 观影助手</h1>
              )}
              <span className="agent-workspace-status">
                <i />
                安全入口 · {STATUS_TEXT[workspace.projection.status]}
              </span>
            </div>
          </div>
          <div className="agent-workspace-actions">
            <Tooltip title="新建会话">
              <Button
                aria-label="新建会话"
                className="agent-icon-button"
                icon={<WorkspaceIcon kind="add" />}
                onClick={() => void createSession()}
                type="text"
              />
            </Tooltip>
            <Tooltip title="历史会话">
              <Button
                aria-label="历史会话"
                className="agent-icon-button"
                icon={<WorkspaceIcon kind="history" />}
                onClick={() => setHistoryOpen((open) => !open)}
                type="text"
              />
            </Tooltip>
            <Tooltip title="清空当前会话">
              <Button
                aria-label="清空当前会话"
                className="agent-icon-button"
                icon={<WorkspaceIcon kind="clear" />}
                onClick={() => void clearCurrentSession()}
                type="text"
              />
            </Tooltip>
            {busy && workspace.projection.runId && (
              <Button danger onClick={() => void workspace.cancel()}>
                取消运行
              </Button>
            )}
          </div>
        </header>

        <Collapse
          activeKey={historyOpen ? ['sessions'] : []}
          className="agent-history-collapse"
          items={[
            {
              key: 'sessions',
              label: '历史会话',
              children: (
                <div>
                  <div className="agent-history-clear">
                    <span>按第一条消息区分会话</span>
                    <Tooltip title="清空全部历史">
                      <Button
                        aria-label="清空全部历史"
                        className="agent-icon-button"
                        icon={<WorkspaceIcon kind="clear" />}
                        onClick={() => void clearAllSessions()}
                        type="text"
                      />
                    </Tooltip>
                  </div>
                  {sessionHistory}
                </div>
              ),
            },
          ]}
          onChange={(keys) => setHistoryOpen(keys.includes('sessions'))}
        />

        {workspace.feedback && <Alert type="info" showIcon message={workspace.feedback} />}
        {workspace.projection.safeError && (
          <Alert type="warning" showIcon message={workspace.projection.safeError} />
        )}

        <div className="agent-message-area" aria-live="polite">
          {workspace.loadStatus === 'loading' ? (
            <div className="agent-workspace-loading">
              <Spin />
              <span>正在加载会话</span>
            </div>
          ) : workspace.loadStatus === 'error' ? (
            <Empty description={workspace.feedback || '会话暂时不可用'} />
          ) : workspace.projection.items.length === 0 ? (
            <Empty description="说说你想看什么电影" />
          ) : (
            <div className="agent-message-list" role="list">
              {workspace.projection.items.map((item, index) => (
                <AgentDisplayItemView
                  key={item.key}
                  item={item}
                  sessionId={sessionId}
                  planPresentation={variant === 'recommendations' ? 'summary' : 'full'}
                  planDetailPath={variant === 'recommendations'
                    ? `${routeBase}/${encodeURIComponent(sessionId)}/plan`
                    : undefined}
                  selectSeatsEnabled={variant === 'debug' || index > latestPlanIndex}
                  answerDisabled={busy}
                  onAnswer={(_itemKey, answer) => workspace.submitQuestionAnswer(answer)}
                  onConfirm={(itemKey, confirmed) => void workspace.confirm(itemKey, confirmed)}
                />
              ))}
            </div>
          )}
        </div>

        <footer className="agent-composer">
          <Input.TextArea
            aria-label="观影需求"
            maxLength={2000}
            value={draft}
            placeholder="例如：周末想看一部轻松的电影"
            autoSize={{ minRows: 2, maxRows: 5 }}
            disabled={busy}
            onChange={(event) => setDraft(event.target.value)}
            onPressEnter={(event) => {
              if (!event.shiftKey) {
                event.preventDefault();
                void send();
              }
            }}
          />
          <Button type="primary" disabled={busy || !draft.trim()} onClick={() => void send()}>
            {busy ? '处理中' : '发送'}
          </Button>
        </footer>
        <nav className="agent-safe-fallback" aria-label="固定购票入口">
          Agent 暂不可用时可前往 <Link to="/movies">影片列表</Link> 或{' '}
          <Link to="/cinemas">影院列表</Link>。
        </nav>
      </div>
    </section>
  );
}
