import { Alert, Button, Drawer, Empty, Input, Spin, Tag } from 'antd';
import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'umi';

import { useAgentWorkspace } from '../../modules/agent/useAgentWorkspace';
import { takePendingAgentDraft } from '../../modules/agent/entryDraft';
import type { AgentSession } from '../../modules/agent/types';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
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
  COMPLETED: '已完成',
  FAILED: '未完成',
  RESULT_UNKNOWN: '结果待确认',
  CANCELLED: '已取消',
} as const;

/** Agent 工作区视图；桌面和移动布局共享同一个 useAgentWorkspace 状态。 */
export function AgentWorkspace({ sessionId }: { sessionId: string }) {
  const navigate = useNavigate();
  const isMobile = useMediaQuery('(max-width: 1023px)');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const workspace = useAgentWorkspace(sessionId);
  const busy = ['CONNECTING', 'STREAMING', 'RESULT_UNKNOWN'].includes(workspace.projection.status);

  const selectSession = (nextSessionId: string) => {
    workspace.stopActiveStream();
    setDrawerOpen(false);
    navigate(`/assistant/${encodeURIComponent(nextSessionId)}`);
  };

  const createSession = async () => {
    const created = await workspace.createNewSession();
    if (created) selectSession(created.sessionId);
  };

  const send = async () => {
    const sent = await workspace.submit(draft);
    if (sent) setDraft('');
  };

  useEffect(() => {
    if (workspace.loadStatus !== 'ready') return;
    const pendingDraft = takePendingAgentDraft();
    if (pendingDraft) void workspace.submit(pendingDraft);
  }, [workspace.loadStatus, workspace.submit]);

  const sidebar = (
    <div className="agent-workspace-sidebar-content">
      <Button type="primary" block onClick={() => void createSession()}>
        新建会话
      </Button>
      <SessionList
        activeSessionId={sessionId}
        sessions={workspace.sessions}
        onSelect={selectSession}
      />
      <Button block onClick={() => void workspace.clearAll()}>
        清空本人会话
      </Button>
    </div>
  );

  return (
    <section className="agent-workspace" aria-label="妙语 Agent 工作区">
      {!isMobile && <aside className="agent-workspace-sidebar">{sidebar}</aside>}
      <div className="agent-workspace-main">
        <header className="agent-workspace-header">
          <div>
            <h1>妙语观影助手</h1>
            <Tag>{STATUS_TEXT[workspace.projection.status]}</Tag>
          </div>
          <div className="agent-workspace-actions">
            {isMobile && <Button onClick={() => setDrawerOpen(true)}>会话列表</Button>}
            {busy && workspace.projection.runId && (
              <Button danger onClick={() => void workspace.cancel()}>
                取消运行
              </Button>
            )}
            <Button onClick={() => void workspace.clearCurrent()}>清空当前</Button>
          </div>
        </header>

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
              {workspace.projection.items.map((item) => (
                <AgentDisplayItemView
                  key={item.key}
                  item={item}
                  answerDisabled={busy}
                  onAnswer={(_itemKey, answer) => workspace.submit(answer)}
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

      <Drawer
        title="会话列表"
        placement="left"
        width="min(88vw, 360px)"
        open={isMobile && drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        {sidebar}
      </Drawer>
    </section>
  );
}
