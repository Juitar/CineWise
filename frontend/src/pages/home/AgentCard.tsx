import React, { useState } from 'react';
import { RobotIcon } from '../../shared/components/icons/layout-icons';

interface AgentCardProps {
  onSubmit(draft: string): void;
}

export const AgentCard: React.FC<AgentCardProps> = ({ onSubmit }) => {
  const [draft, setDraft] = useState('');

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (draft.trim()) onSubmit(draft);
  };
  return (
    <div className="home-agent-panel">
      {/* 头部 */}
      <div className="home-agent-header">
        <div className="home-agent-header-left">
          <div className="home-agent-icon-bg">
            <RobotIcon size={16} />
          </div>
          <span className="home-agent-title">妙语 AI 观影助手</span>
        </div>
        <div className="home-agent-header-right">
          <span className="home-agent-status-dot"></span>
          <span className="home-agent-status-text">安全入口</span>
        </div>
      </div>

      <div className="home-agent-body">
        <div className="home-agent-entry-copy">
          <h3>从真实需求开始规划</h3>
          <p>输入影片、时间、人数或预算，进入 Agent 工作区查看服务端返回的方案和进度。</p>
          <p>路线、距离和附近餐饮只会在你主动使用对应功能并确认后查询。</p>
        </div>
      </div>

      <form className="home-agent-footer" onSubmit={submit}>
        <div className="agent-input-wrapper">
          <input
            className="agent-input-field"
            aria-label="首页 Agent 输入"
            maxLength={2000}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="继续输入你的观影需求吧..."
          />
          <button
            type="submit"
            className="agent-send-btn"
            aria-label="发送"
            disabled={!draft.trim()}
          >
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
            </svg>
          </button>
        </div>
        <div className="agent-suggestions">
          <span className="suggestion-label">可以问我：</span>
          <span className="suggestion-chip">IMAX电影有哪些？</span>
          <span className="suggestion-chip">带孩子看什么电影好？</span>
        </div>
      </form>
    </div>
  );
};
