import { Alert, Button, Collapse, Empty, Input, Spin, Tag, Tooltip } from 'antd';
import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'umi';

import { takePendingAgentDraft } from '../../modules/agent/entryDraft';
import {
  buildAgentSelectSeatsPath,
  type AgentDisplayItem,
  type AgentPlanDisplay,
} from '../../modules/agent/projection';
import type { AgentSession } from '../../modules/agent/types';
import { useAgentWorkspace } from '../../modules/agent/useAgentWorkspace';
import { safePosterUrl } from '../../modules/content/poster';
import { useCinemaList } from '../../modules/content/useCinemaList';
import { useMovieList } from '../../modules/content/useMovieList';
import { CinemaIcon, FilmIcon, RobotIcon } from '../../shared/components/icons/layout-icons';
import { AgentDisplayItemView } from './cards';
import './index.css';

interface SessionListProps {
  activeSessionId: string;
  sessions: readonly AgentSession[];
  onSelect(sessionId: string): void;
}

function SessionList({ activeSessionId, sessions, onSelect }: SessionListProps) {
  if (sessions.length === 0) return <Empty description="暂无历史会话" />;
  return (
    <div className="agent-session-list" role="list" aria-label="Agent 会话">
      {sessions.map((session) => (
        <button
          type="button"
          role="listitem"
          className={`agent-session-item${session.sessionId === activeSessionId ? ' is-active' : ''}`}
          key={session.sessionId}
          onClick={() => onSelect(session.sessionId)}
        >
          <span>{session.summary || '新会话'}</span>
          <small>{session.status}</small>
        </button>
      ))}
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
}

interface SelectedPlanRef {
  itemKey: string;
  index: number;
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

function WorkspaceIcon({ kind }: { kind: 'add' | 'clear' | 'history' }) {
  if (kind === 'add') {
    return <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" /></svg>;
  }
  if (kind === 'clear') {
    return <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M9 7l1-3h4l1 3M7 7l1 13h8l1-13" /></svg>;
  }
  return <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 5h16v14H4zM8 9h8M8 13h5" /></svg>;
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
        <p>右侧助手会把你的条件整理成可购方案；选片、选档期、选座和确认订单都留在这个工作区完成。</p>
      </div>
      <section className="agent-discovery-section" aria-labelledby="agent-discovery-movies">
        <div className="agent-discovery-section-head">
          <div><FilmIcon size={19} /><h2 id="agent-discovery-movies">正在热映</h2></div>
          <Link to="/movies">全部影片</Link>
        </div>
        <div className="agent-discovery-movie-grid">
          {movies.data?.records.map((movie) => {
            const poster = safePosterUrl(movie.posterUrl);
            return (
              <Link className="agent-discovery-movie" key={movie.movieId}
                to={`${workspaceBase}/movies/${encodeURIComponent(movie.movieId)}`}>
                {poster ? <img alt="" src={poster} /> : <div className="agent-discovery-poster-fallback"><FilmIcon size={24} /></div>}
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
          <div><CinemaIcon size={19} /><h2 id="agent-discovery-cinemas">长沙影院</h2></div>
          <Link to="/cinemas">全部影院</Link>
        </div>
        <div className="agent-discovery-cinema-grid">
          {cinemas.data?.records.map((cinema) => (
            <Link className="agent-discovery-cinema" key={cinema.cinemaId}
              to={`/cinemas/${encodeURIComponent(cinema.cinemaId)}`}>
              <strong>{cinema.name}</strong><span>{cinema.area || '长沙'}</span>
            </Link>
          ))}
          {cinemas.isLoading && <Spin />}
        </div>
      </section>
    </section>
  );
}

function RecommendationPlanPane({
  item,
  onSelect,
  selectedIndex,
  sessionId,
}: {
  item: AgentDisplayItem | null;
  onSelect(index: number): void;
  selectedIndex: number | null;
  sessionId: string;
}) {
  const selectedPlan =
    selectedIndex === null || item?.plans === undefined ? null : item.plans[selectedIndex] ?? null;
  const selectedPlanExpired =
    selectedPlan === null || selectedPlan.expired || Date.parse(selectedPlan.expiresAt) <= Date.now();
  const selectSeatsPath =
    selectedPlan !== null && selectedPlan.purchaseEligible && !selectedPlanExpired
      ? `/recommendations/${encodeURIComponent(sessionId)}${buildAgentSelectSeatsPath(
          selectedPlan.showId, selectedPlan.movieId, selectedPlan.cinemaId,
        )}`
      : null;
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
            const expired = plan.expired || Date.parse(plan.expiresAt) <= Date.now();
            const selected = selectedIndex === index;
            return (
              <div key={`${item.key}:${plan.showId}:${index}`} role="listitem">
                <button
                  aria-pressed={selected}
                  className={`agent-recommendation-option${selected ? ' is-selected' : ''}`}
                  onClick={() => onSelect(index)}
                  type="button"
                >
                  <div className="agent-recommendation-option-heading">
                    <span>{PLAN_TYPE_TEXT[plan.planType] ?? plan.planType}</span>
                    <strong>
                      {plan.currency === 'CNY' ? '¥' : `${plan.currency} `}
                      {plan.price}
                    </strong>
                  </div>
                  <h2>{plan.movieName}</h2>
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
                    <span>{plan.purchaseEligible ? '可直接进入选座' : '当前不可直接购买'}</span>
                    <span>以进入选座页后的最新座位状态为准</span>
                  </div>
                  {(expired || !plan.purchaseEligible) && (
                    <span className="agent-recommendation-unavailable">
                      {expired ? '方案已过期' : '当前不可购'}
                    </span>
                  )}
                  {selected && <span className="agent-recommendation-selected">当前选择</span>}
                </button>
              </div>
            );
          })}
        </div>
      )}

      {item?.relaxationSuggestion && (
        <p className="agent-recommendation-relaxation">可调整条件：{item.relaxationSuggestion}</p>
      )}
      {selectSeatsPath && (
        <Link className="agent-recommendation-seat-link" to={selectSeatsPath}>
          去选座：{selectedPlan?.movieName}
        </Link>
      )}
      <p className="agent-recommendation-note">
        方案来自 Agent 实时结果。进入选座前仍以票务服务的最新场次状态为准。
      </p>
    </section>
  );
}

/** Agent 工作区视图；桌面和移动布局共享同一个 useAgentWorkspace 状态。 */
export function AgentWorkspace({ sessionId, variant = 'debug', businessContent }: AgentWorkspaceProps) {
  const navigate = useNavigate();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const [selectedPlanRef, setSelectedPlanRef] = useState<SelectedPlanRef | null>(null);
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

  const selectSession = (nextSessionId: string) => {
    workspace.stopActiveStream();
    navigate(`${routeBase}/${encodeURIComponent(nextSessionId)}`);
  };

  const createSession = async () => {
    const created = await workspace.createNewSession();
    if (created) selectSession(created.sessionId);
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

  useEffect(() => {
    setSelectedPlanRef(null);
  }, [sessionId]);

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
        onSelect={selectSession}
      />
    </div>
  );

  return (
    <section
      className={`agent-workspace agent-workspace--${variant}`}
      aria-label="妙语 Agent 工作区"
    >
      {variant === 'debug' && (
        <aside className="agent-workspace-sidebar">{sessionHistory}</aside>
      )}
      {variant === 'recommendations' && (
        <div className="agent-workspace-business-pane">
          {businessContent ?? (latestPlanItem ? (
            <RecommendationPlanPane
              item={latestPlanItem}
              onSelect={selectPlan}
              selectedIndex={selectedIndex}
              sessionId={sessionId}
            />
          ) : <AgentDiscoveryPane sessionId={sessionId} />)}
        </div>
      )}
      <div className="agent-workspace-main">
        <header className="agent-workspace-header">
          <div className="agent-workspace-brand">
            <span className="agent-workspace-brand-icon"><RobotIcon size={18} /></span>
            <div>
              {variant === 'recommendations' ? <h2>妙语 AI 观影助手</h2> : <h1>妙语 AI 观影助手</h1>}
              <span className="agent-workspace-status"><i />安全入口 · {STATUS_TEXT[workspace.projection.status]}</span>
            </div>
          </div>
          <div className="agent-workspace-actions">
            <Tooltip title="新建会话"><Button aria-label="新建会话" className="agent-icon-button" icon={<WorkspaceIcon kind="add" />} onClick={() => void createSession()} type="text" /></Tooltip>
            <Tooltip title="历史会话"><Button aria-label="历史会话" className="agent-icon-button" icon={<WorkspaceIcon kind="history" />} onClick={() => setHistoryOpen((open) => !open)} type="text" /></Tooltip>
            <Tooltip title="清空当前会话"><Button aria-label="清空当前会话" className="agent-icon-button" icon={<WorkspaceIcon kind="clear" />} onClick={() => void workspace.clearCurrent()} type="text" /></Tooltip>
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
          items={[{
            key: 'sessions',
            label: '历史会话',
            children: <div><div className="agent-history-clear"><span>按第一条消息区分会话</span><Tooltip title="清空全部历史"><Button aria-label="清空全部历史" className="agent-icon-button" icon={<WorkspaceIcon kind="clear" />} onClick={() => void workspace.clearAll()} type="text" /></Tooltip></div>{sessionHistory}</div>,
          }]}
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
                  planPresentation={variant === 'recommendations' ? 'summary' : 'full'}
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
