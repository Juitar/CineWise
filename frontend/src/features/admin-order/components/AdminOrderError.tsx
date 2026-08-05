import { Button } from 'antd';
import React from 'react';
import type { AdminOrderState } from '../types';
import './AdminOrderError.css';

export interface AdminOrderErrorProps {
  state: AdminOrderState;
  traceId?: string;
  onRetry?: () => void;
}

export const AdminOrderError: React.FC<AdminOrderErrorProps> = ({ state, traceId, onRetry }) => {
  if (state === 'NORMAL' || state === 'LOADING' || state === 'EMPTY') return null;

  let title = '系统错误';
  let desc = '获取订单列表失败，请稍后重试。';

  if (state === 'FORBIDDEN') {
    title = '无权限访问';
    desc = '当前用户无管理权限，请联系超级管理员。';
  } else if (state === 'QUERY_TOO_BROAD') {
    title = '用户查询条件过宽';
    desc = '匹配到的用户数超过限制，请输入更精确的用户标识。';
  } else if (state === 'DIRECTORY_UNAVAILABLE') {
    title = '用户信息查询暂不可用';
    desc = '当前暂时无法检索用户信息，请稍后重试。';
  }

  const showRetry = state === 'DIRECTORY_UNAVAILABLE' || state === 'GENERAL_ERROR';

  return (
    <div className="admin-orders-error-container">
      <div className="admin-orders-error-title">{title}</div>
      <div className="admin-orders-error-desc">{desc}</div>
      {showRetry && onRetry && (
        <Button type="primary" onClick={onRetry}>
          手动重试
        </Button>
      )}
      {traceId && <div className="admin-orders-error-trace">TraceId: {traceId}</div>}
    </div>
  );
};
