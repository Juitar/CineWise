import { Alert, Button, Spin } from 'antd';
import type { PropsWithChildren } from 'react';
import { Navigate, Outlet, useLocation } from 'umi';

import { useAuth } from './AuthProvider';
import { createLoginRedirect } from './safeReturnUrl';
import './AuthGuard.css';

/** 未登录时跳转用户登录页；会话检查失败时保留重试入口。 */
export default function RequireAuth({ children }: PropsWithChildren) {
  const location = useLocation();
  const { retrySessionCheck, status } = useAuth();

  if (status === 'checking') {
    return (
      <div className="auth-guard-state" aria-label="正在检查登录状态">
        <Spin size="large" />
      </div>
    );
  }
  if (status === 'error') {
    return (
      <div className="auth-guard-state">
        <Alert
          className="auth-guard-alert"
          type="error"
          showIcon
          message="暂时无法确认登录状态"
          description="请检查网络后重试。"
          action={<Button onClick={() => void retrySessionCheck()}>重新检查</Button>}
        />
      </div>
    );
  }
  if (status === 'anonymous') {
    return (
      <Navigate
        replace
        to={createLoginRedirect(location.pathname, location.search, location.hash)}
      />
    );
  }

  return <>{children ?? <Outlet />}</>;
}
