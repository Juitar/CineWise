import React from 'react';
import { Button } from 'antd';
import { Button as MobileButton } from 'antd-mobile';

import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

export interface OrderCreateSuccessProps {
  /** 订单号 */
  orderNo: string;
  /** 应付金额 */
  totalAmount: string;
  /** 支付期限提示文本，例如 "14分59秒" */
  expireTimeText: string;
  /** 点击去支付 */
  onPay?: () => void;
  /** 点击查看订单 */
  onViewOrder?: () => void;
  /** 是否处于加载中（点击支付后） */
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
  loading,
}) => {
  const isMobile = useMediaQuery('(max-width: 1023px)');

  const content = (
    <>
      <div className="order-success-icon" aria-hidden="true">
        <svg
          viewBox="64 64 896 896"
          focusable="false"
          data-icon="check-circle"
          width="1em"
          height="1em"
          fill="currentColor"
          aria-hidden="true"
          className="order-success-icon-svg"
        >
          <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm193.5 301.7l-210.6 292a31.8 31.8 0 01-51.7 0L318.5 484.9c-3.8-5.3 0-12.7 6.5-12.7h46.9c10.2 0 19.9 4.9 25.9 13.3l71.2 98.8 157.2-218c6-8.3 15.6-13.3 25.9-13.3H699c6.5 0 10.3 7.4 6.5 12.7z"></path>
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
          <span className="order-success-amount">
            <span className="order-success-currency">¥</span>
            {totalAmount}
          </span>
        </div>
        <div className="order-success-row">
          <span className="order-success-label">支付期限</span>
          <span className="order-success-value order-success-highlight">{expireTimeText}</span>
        </div>
      </div>

      <div className="order-success-actions">
        {isMobile ? (
          <>
            <MobileButton
              color="primary"
              size="large"
              loading={loading}
              disabled={loading || !onPay}
              onClick={onPay}
              className="order-success-btn"
            >
              去支付
            </MobileButton>
            <MobileButton
              size="large"
              disabled={loading || !onViewOrder}
              onClick={onViewOrder}
              className="order-success-btn order-success-btn-secondary"
            >
              查看订单
            </MobileButton>
          </>
        ) : (
          <>
            <Button
              type="primary"
              size="large"
              loading={loading}
              disabled={loading || !onPay}
              onClick={onPay}
              className="order-success-btn"
            >
              去支付
            </Button>
            <Button
              size="large"
              disabled={loading || !onViewOrder}
              onClick={onViewOrder}
              className="order-success-btn"
            >
              查看订单
            </Button>
          </>
        )}
      </div>
    </>
  );

  return (
    <div className="order-success-container">
      {isMobile ? content : <div className="order-success-card">{content}</div>}
    </div>
  );
};
