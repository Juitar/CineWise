import { Alert, Button, Spin } from 'antd';
import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'umi';

import { AgentWorkspace } from '../../features/agent-workspace';
import { useAgentSessionBootstrap } from '../../modules/agent/useAgentWorkspace';

/** 正常用户的推荐方案入口；会话创建和运行恢复继续由共享 Agent Hook 负责。 */
export default function RecommendationsPage() {
  const { sessionId } = useParams<{ sessionId?: string }>();
  const navigate = useNavigate();
  const bootstrap = useAgentSessionBootstrap(!sessionId);

  useEffect(() => {
    if (bootstrap.createdSessionId) {
      navigate(`/recommendations/${encodeURIComponent(bootstrap.createdSessionId)}`, {
        replace: true,
      });
    }
  }, [bootstrap.createdSessionId, navigate]);

  if (sessionId) {
    return <AgentWorkspace sessionId={sessionId} variant="recommendations" />;
  }
  if (bootstrap.error) {
    return (
      <div className="auth-guard-state">
        <Alert type="error" showIcon message={bootstrap.error} />
        {bootstrap.resultUnknown ? (
          <Link to="/">返回首页</Link>
        ) : (
          <Button onClick={() => void bootstrap.retry()}>重新创建</Button>
        )}
      </div>
    );
  }
  return (
    <div className="auth-guard-state">
      <Spin size="large" />
      <span>正在创建推荐方案工作区</span>
    </div>
  );
}
