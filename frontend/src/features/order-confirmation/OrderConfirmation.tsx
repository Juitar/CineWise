import React from 'react';
import { Button, Spin, Result } from 'antd';
import { Button as MobileButton, SpinLoading, ErrorBlock } from 'antd-mobile';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

export interface OrderConfirmationProps {
  /** 影厅名称 */
  auditoriumName: string;
  /** 已选座位的中文描述集合 */
  seatLabels: string[];
  /** 票数 */
  ticketCount: number;
  /** 总价 */
  totalAmount: string;
  /** 加载中状态 */
  loading?: boolean;
  /** 建单请求正在提交，期间禁止再次提交或返回修改 */
  submitting?: boolean;
  /** 正在查询原建单结果 */
  recovering?: boolean;
  /** 外层校验未通过时禁用提交 */
  submitDisabled?: boolean;
  /** 一般错误信息 */
  error?: Error | null;
  /** 是否出现座位冲突 (204001) */
  isConflict?: boolean;
  /** 座位不可售 */
  isNotAvailable?: boolean;
  /** 是否处于 RESULT_UNKNOWN 状态 */
  isResultUnknown?: boolean;
  /** 提交订单 */
  onSubmit: () => void;
  /** 发生未知错误时的重试查询 */
  onRetry: () => void;
  /** 返回修改/取消 */
  onCancel: () => void;
  /** 查询上下文失败时由外层提供只读重试；缺省时使用返回修改 */
  onErrorAction?: () => void;
  /** 查询上下文失败时的操作文案 */
  errorActionLabel?: string;
}

/**
 * 订单确认纯展示组件。
 * 负责渲染选座信息及总金额，并处理 RESULT_UNKNOWN 等异常状态视图。
 * 所有接口调用与页面跳转逻辑需通过外层传入回调执行。
 */
export const OrderConfirmation: React.FC<OrderConfirmationProps> = ({
  auditoriumName,
  seatLabels,
  ticketCount,
  totalAmount,
  loading,
  submitting,
  recovering,
  submitDisabled,
  error,
  isConflict,
  isNotAvailable,
  isResultUnknown,
  onSubmit,
  onRetry,
  onCancel,
  onErrorAction,
  errorActionLabel = '返回修改',
}) => {
  const isMobile = useMediaQuery('(max-width: 1023px)');

  if (loading) {
    return (
      <div className="order-confirm-loading">
        {isMobile ? <SpinLoading color="primary" /> : <Spin size="large" />}
      </div>
    );
  }

  // RESULT_UNKNOWN 状态：只能重试查询，不提供其他操作入口
  if (isResultUnknown) {
    return (
      <div className="order-confirm-result-unknown">
        {isMobile ? (
          <div className="order-confirm-error-wrapper">
            <ErrorBlock status="default" description="网络异常或超时，订单结果未知" />
            <MobileButton
              color="primary"
              loading={recovering}
              disabled={recovering}
              onClick={onRetry}
              className="order-confirm-btn"
            >
              {recovering ? '正在查询订单结果...' : '重新查询订单结果'}
            </MobileButton>
          </div>
        ) : (
          <Result
            status="warning"
            title="订单结果未知"
            subTitle="网络异常或超时，无法确认订单状态，请点击下方按钮查询最终结果。"
            extra={[
              <Button
                type="primary"
                key="retry"
                loading={recovering}
                disabled={recovering}
                onClick={onRetry}
                className="order-confirm-btn"
              >
                {recovering ? '正在查询订单结果...' : '重新查询订单结果'}
              </Button>,
            ]}
          />
        )}
      </div>
    );
  }

  // 业务冲突或异常状态
  if (isConflict || isNotAvailable || error) {
    const errorMsg = isConflict
      ? '所选座位已被他人锁定，请返回重新选座'
      : isNotAvailable
        ? '所选座位当前不可售，请返回重新选座'
        : error?.message || '加载订单确认信息失败';

    return (
      <div className="order-confirm-error">
        {isMobile ? (
          <div className="order-confirm-error-wrapper">
            <ErrorBlock status="default" description={errorMsg} />
            <MobileButton onClick={onErrorAction ?? onCancel} className="order-confirm-btn">
              {errorActionLabel}
            </MobileButton>
          </div>
        ) : (
          <Result
            status="error"
            title="无法确认订单"
            subTitle={errorMsg}
            extra={[
              <Button
                key="cancel"
                onClick={onErrorAction ?? onCancel}
                className="order-confirm-btn"
              >
                {errorActionLabel}
              </Button>,
            ]}
          />
        )}
      </div>
    );
  }

  return (
    <div className="order-confirm-container">
      <div className="order-confirm-card">
        <h2 className="order-confirm-title">确认订单信息</h2>
        <div className="order-confirm-details">
          <div className="order-confirm-row">
            <span className="order-confirm-label">影厅</span>
            <span className="order-confirm-value">{auditoriumName}</span>
          </div>
          <div className="order-confirm-row">
            <span className="order-confirm-label">座位</span>
            <span className="order-confirm-value">{seatLabels.join('、')}</span>
          </div>
          <div className="order-confirm-row">
            <span className="order-confirm-label">数量</span>
            <span className="order-confirm-value">{ticketCount} 张</span>
          </div>
        </div>
      </div>

      <div className="order-confirm-footer">
        <div className="order-confirm-total">
          <span className="order-confirm-total-label">应付总计：</span>
          <span className="order-confirm-total-amount">
            <span className="order-confirm-currency">¥</span>
            {totalAmount}
          </span>
        </div>
        <div className="order-confirm-actions">
          {isMobile ? (
            <>
              <MobileButton
                disabled={submitting}
                onClick={onCancel}
                className="order-confirm-cancel-btn"
              >
                返回修改
              </MobileButton>
              <MobileButton
                color="primary"
                loading={submitting}
                disabled={submitting || submitDisabled}
                onClick={onSubmit}
                className="order-confirm-submit-btn"
              >
                确认并提交订单
              </MobileButton>
            </>
          ) : (
            <>
              <Button
                disabled={submitting}
                onClick={onCancel}
                size="large"
                className="order-confirm-cancel-btn"
              >
                返回修改
              </Button>
              <Button
                type="primary"
                loading={submitting}
                disabled={submitting || submitDisabled}
                onClick={onSubmit}
                size="large"
                className="order-confirm-submit-btn"
              >
                确认并提交订单
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
