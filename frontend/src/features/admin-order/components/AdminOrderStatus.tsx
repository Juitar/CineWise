import { Tag } from 'antd';
import React from 'react';
import type { OrderStatus, PaymentStatus, RefundStatus, TicketStatus } from '../types';
import './AdminOrderStatus.css';

export const renderOrderStatus = (status: OrderStatus | string | null | undefined) => {
  if (status === null || status === undefined)
    return <span className="admin-order-status-empty">未产生</span>;
  const statusMap: Record<string, { color: string; text: string }> = {
    PENDING_PAYMENT: { color: 'warning', text: '待支付' },
    PAYING: { color: 'processing', text: '支付确认中' },
    PAID: { color: 'success', text: '已支付' },
    CANCELLED: { color: 'default', text: '已取消' },
    EXPIRED: { color: 'default', text: '已过期' },
    REFUNDING: { color: 'processing', text: '退款处理中' },
    REFUNDED: { color: 'default', text: '已退款' },
  };
  const config = statusMap[status] || { color: 'default', text: '未知状态' };
  return <Tag color={config.color}>{config.text}</Tag>;
};

export const renderPaymentStatus = (status: PaymentStatus | string | null | undefined) => {
  if (status === null || status === undefined)
    return <span className="admin-order-status-empty">未产生</span>;
  const statusMap: Record<string, { color: string; text: string }> = {
    INITIALIZED: { color: 'default', text: '已初始化' },
    PROCESSING: { color: 'processing', text: '处理中' },
    SUCCESS: { color: 'success', text: '支付成功' },
  };
  const config = statusMap[status] || { color: 'default', text: '未知状态' };
  return <Tag color={config.color}>{config.text}</Tag>;
};

export const renderTicketStatus = (status: TicketStatus | string | null | undefined) => {
  if (status === null || status === undefined)
    return <span className="admin-order-status-empty">未产生</span>;
  const statusMap: Record<string, { color: string; text: string }> = {
    VALID: { color: 'success', text: '有效' },
    REFUNDED: { color: 'default', text: '已退款失效' },
    INVALIDATED: { color: 'error', text: '已失效' },
  };
  const config = statusMap[status] || { color: 'default', text: '未知状态' };
  return <Tag color={config.color}>{config.text}</Tag>;
};

export const renderRefundStatus = (status: RefundStatus | string | null | undefined) => {
  if (status === null || status === undefined)
    return <span className="admin-order-status-empty">未产生</span>;
  const statusMap: Record<string, { color: string; text: string }> = {
    REQUESTED: { color: 'default', text: '已申请' },
    PROCESSING: { color: 'processing', text: '处理中' },
    SUCCESS: { color: 'success', text: '退款成功' },
  };
  const config = statusMap[status] || { color: 'default', text: '未知状态' };
  return <Tag color={config.color}>{config.text}</Tag>;
};
