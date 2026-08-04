import { Alert, Button, Spin } from 'antd';
import type { PropsWithChildren } from 'react';
import { Navigate, Outlet, useLocation } from 'umi';

import { useAuth } from './AuthProvider';
import { createLoginRedirect } from './safeReturnUrl';
import './AuthGuard.css';

/** 管理路由先恢复服务端身份；普通用户进入统一 403 页面。 */
export default function RequireAdmin({ children }: PropsWithChildren) {
  const location = useLocation();
  const { currentUser, retrySessionCheck, status } = useAuth();

  if (status === 'checking') {
    return (
      <div className="auth-guard-state" aria-label="正在检查管理员身份">
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
          message="暂时无法确认管理员身份"
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
  if (currentUser?.role !== 'ADMIN') {
    return <Navigate replace to="/403" />;
  }

  return <>{children ?? <Outlet />}</>;
}
