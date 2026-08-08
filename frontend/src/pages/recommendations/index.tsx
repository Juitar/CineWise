import { Alert, Button, Spin } from 'antd';
import React, { useEffect } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'umi';

import { AgentWorkspace } from '../../features/agent-workspace';
import { useAgentSessionBootstrap } from '../../modules/agent/useAgentWorkspace';
import AvailableCinemasPage from '../movies/available-cinemas';
import ShowsPage from '../shows';
import SeatsPage from '../seats';
import OrderConfirmPage from '../orders/confirm';
import OrderDetailPage from '../orders/detail';
import PaymentPage from '../payments';

/** 正常用户的推荐方案入口；会话创建和运行恢复继续由共享 Agent Hook 负责。 */
export default function RecommendationsPage() {
  const { sessionId } = useParams<{ sessionId?: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const bootstrap = useAgentSessionBootstrap(!sessionId);

  useEffect(() => {
    if (bootstrap.createdSessionId) {
      navigate(`/recommendations/${encodeURIComponent(bootstrap.createdSessionId)}`, {
        replace: true,
      });
    }
  }, [bootstrap.createdSessionId, navigate]);

  if (sessionId) {
    let businessContent: React.ReactNode = null;
    if (/\/movies\/[^/]+$/.test(location.pathname)) businessContent = <AvailableCinemasPage />;
    else if (/\/shows\/[^/]+\/seats$/.test(location.pathname)) businessContent = <SeatsPage />;
    else if (/\/shows$/.test(location.pathname)) businessContent = <ShowsPage />;
    else if (/\/orders\/confirm$/.test(location.pathname)) businessContent = <OrderConfirmPage />;
    else if (/\/orders\/[^/]+$/.test(location.pathname)) businessContent = <OrderDetailPage />;
    else if (/\/payments\/[^/]+$/.test(location.pathname)) businessContent = <PaymentPage />;
    return (
      <AgentWorkspace
        businessContent={businessContent}
        sessionId={sessionId}
        variant="recommendations"
      />
    );
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
