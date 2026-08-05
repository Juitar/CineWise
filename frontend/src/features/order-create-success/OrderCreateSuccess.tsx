import React from 'react';
import { Button } from 'antd';
import './index.css';

export interface OrderCreateSuccessProps {
  orderNo: string;
  totalAmount: string;
  expireTimeText: string;
  onPay?: () => void;
  onViewOrder?: () => void;
  loading?: boolean;
}

/**
 * 建单成功展示组件。
 * 纯展示组件：负责订单号、金额及倒计时的可视化呈现。
 * 所有业务逻辑（如时间计算、API 请求、路由跳转）必须通过外层容器控制并通过 props 传入。
 * 组件内部不得发起网络请求或处理支付状态迁移逻辑。
 */
export const OrderCreateSuccess: React.FC<OrderCreateSuccessProps> = ({
  orderNo,
  totalAmount,
  expireTimeText,
  onPay,
  onViewOrder,
  loading = false,
}) => {
  return (
    <div className="order-success-container">
      <div className="order-success-icon" aria-hidden="true">
        <svg viewBox="0 0 1024 1024" fill="#52c41a" width="64" height="64">
          <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm193.5 301.7l-210.6 292a31.8 31.8 0 01-51.7 0L318.5 484.9c-3.8-5.3 0-12.7 6.5-12.7h46.9c10.2 0 19.9 4.9 25.9 13.3l71.2 98.8 157.2-218c6-8.3 15.6-13.3 25.9-13.3H700c6.5 0 10.3 7.4 6.5 12.7z" />
        </svg>
      </div>
      <h2 className="order-success-title">订单创建成功</h2>

      <div className="order-success-details">
        <div className="order-success-row">
          <span className="order-success-label">订单号</span>
          <span className="order-success-value">{orderNo}</span>
        </div>
        <div className="order-success-row">
          <span className="order-success-label">应付金额</span>
          <span className="order-success-amount">{totalAmount}</span>
        </div>
        <div className="order-success-row">
          <span className="order-success-label">支付期限</span>
          <span className="order-success-value order-success-highlight">{expireTimeText}</span>
        </div>
      </div>

      <div className="order-success-actions">
        <Button
          type="primary"
          size="large"
          onClick={onPay}
          loading={loading}
          disabled={loading || !onPay}
          className="order-success-btn"
        >
          去支付
        </Button>
        <Button
          size="large"
          onClick={onViewOrder}
          disabled={loading || !onViewOrder}
          className="order-success-btn"
        >
          查看订单
        </Button>
      </div>
    </div>
  );
};
