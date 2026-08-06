import React, { useState } from 'react';
import { RobotIcon } from '../../shared/components/icons/layout-icons';

interface AgentCardProps {
  onViewPlan: () => void;
  onSubmit(draft: string): void;
}

export const AgentCard: React.FC<AgentCardProps> = ({ onSubmit, onViewPlan }) => {
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
          <span className="home-agent-status-text">已连接</span>
        </div>
      </div>

      {/* 聊天内容区 */}
      <div className="home-agent-body">
        {/* 用户气泡 */}
        <div className="home-agent-message user-message">
          <div className="message-bubble user-bubble">
            帮我找一下今天晚上想看的电影，两个人，预算150元以内，离我近一点
            <div className="message-time">10:30</div>
          </div>
        </div>

        {/* 助手回复文字 */}
        <div className="home-agent-message agent-message">
          <div className="agent-text-reply">好的，已为你找到合适的方案：</div>
        </div>

        {/* 提取的需求卡片 */}
        <div className="home-agent-message agent-message">
          <div className="agent-card requirements-card">
            <div className="agent-card-header">
              <span className="agent-card-title">为你提取的需求</span>
              <span className="agent-card-action">编辑</span>
            </div>
            <div className="requirements-list">
              <div className="requirement-item">
                <span className="requirement-icon"></span>
                <span className="requirement-label">观影时间</span>
                <span className="requirement-value">今天 19:00 以后</span>
              </div>
              <div className="requirement-item">
                <span className="requirement-icon"></span>
                <span className="requirement-label">人数</span>
                <span className="requirement-value">2 人</span>
              </div>
              <div className="requirement-item">
                <span className="requirement-icon"></span>
                <span className="requirement-label">预算</span>
                <span className="requirement-value">≤ ¥150</span>
              </div>
              <div className="requirement-item">
                <span className="requirement-icon"></span>
                <span className="requirement-label">距离</span>
                <span className="requirement-value">尽量近</span>
              </div>
              <div className="requirement-item">
                <span className="requirement-icon"></span>
                <span className="requirement-label">偏好</span>
                <span className="requirement-value">剧情 / 喜剧 / 口碑佳</span>
              </div>
            </div>
          </div>
        </div>

        {/* 推荐方案卡片 */}
        <div className="home-agent-message agent-message">
          <div className="agent-card recommendation-card">
            <div className="agent-card-header">
              <span className="agent-card-title">
                推荐方案 <span className="subtitle">(综合匹配度最高)</span>
              </span>
            </div>

            <div className="recommendation-movie">
              <div className="movie-poster"></div>
              <div className="movie-info">
                <div className="movie-name">云边有个小卖部</div>
                <div className="movie-tags">剧情 / 温情</div>
                <div className="movie-rating">
                  豆瓣评分 <span>8.5</span>
                </div>
              </div>
            </div>

            <div className="recommendation-details">
              <div className="detail-item">
                <span className="detail-icon"></span>
                <span className="detail-text">杭州UME影城 (西湖店)</span>
                <span className="detail-right">1.2km</span>
              </div>
              <div className="detail-item">
                <span className="detail-icon"></span>
                <span className="detail-text">今天 19:40 ~ 21:40 (国语 2D)</span>
              </div>
              <div className="detail-item">
                <span className="detail-icon"></span>
                <span className="detail-text">
                  6号厅 <span className="highlight-tag">可选座位充足</span>
                </span>
              </div>
              <div className="detail-item price-row">
                <span className="detail-icon"></span>
                <span className="detail-text">2张票</span>
                <span className="detail-price">
                  总价 <span className="price-num">¥96</span>
                </span>
                <button type="button" className="view-plan-btn" onClick={onViewPlan}>
                  查看方案
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 底部输入区 */}
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
