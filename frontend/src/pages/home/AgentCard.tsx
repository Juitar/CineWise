import React from 'react';
import { Link } from 'umi';

import { RobotIcon } from '../../shared/components/icons/layout-icons';

export const AgentCard: React.FC = () => {
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
          <p>进入智能购票工作区，根据你的需求查看真实方案和执行进度。</p>
          <p>路线、距离和附近餐饮只会在你主动使用对应功能并确认后查询。</p>
        </div>
      </div>

      <div className="home-agent-footer">
        <Link className="home-agent-entry-link" to="/recommendations">
          进入智能购票之旅
        </Link>
      </div>
    </div>
  );
};
