import React, { useState } from 'react';
import { Button, Alert, Input, Spin } from 'antd';
import { Button as MobileButton, ErrorBlock, SpinLoading } from 'antd-mobile';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

export type PaymentPanelStatus =
  'NORMAL' | 'PROCESSING' | 'RESULT_UNKNOWN' | 'SUCCESS' | 'ERROR' | 'LOADING';

export interface PaymentPanelProps {
  orderNo: string;
  ticketCount: number;
  totalAmount: string;
  showTime?: string;
  paymentDeadlineText?: string;
  status?: PaymentPanelStatus;
  error?: string;
  isOfflineReadOnly?: boolean;
  onPay?: () => void;
  onQueryOrderResult?: () => void;
  onCancelPayment?: () => void;
}

/**
 * 模拟支付面板展示并在组件内短暂校验六位数字输入。
 * 密码在触发无参数 onPay 回调前清空，不能传给页面容器、请求层或持久化存储。
 */
export const PaymentPanel: React.FC<PaymentPanelProps> = ({
  orderNo,
  ticketCount,
  totalAmount,
  showTime,
  paymentDeadlineText,
  status = 'NORMAL',
  error,
  isOfflineReadOnly = false,
  onPay,
  onQueryOrderResult,
  onCancelPayment,
}) => {
  const isMobile = useMediaQuery('(max-width: 1023px)');
  const [password, setPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string>();

  if (status === 'LOADING') {
    return (
      <div className="payment-panel-container">
        {isMobile ? (
          <div className="mobile-loading-wrapper">
            <SpinLoading color="primary" />
            <span>正在加载模拟支付面板...</span>
          </div>
        ) : (
          <Spin tip="正在加载模拟支付面板..." />
        )}
      </div>
    );
  }

  const handlePay = () => {
    if (!/^\d{6}$/.test(password)) {
      setPasswordError('请输入六位数字');
      return;
    }
    // 密码只用于浏览器本地格式确认；调用业务回调前立即从组件内存清除。
    setPassword('');
    setPasswordError(undefined);
    onPay?.();
  };

  return (
    <div className="payment-panel-container">
      {isOfflineReadOnly && (
        <Alert
          className="payment-panel-alert"
          type="info"
          showIcon
          message="离线只读提示"
          description="当前为离线或网络故障只读模式，不提供表单操作。"
        />
      )}

      {error && (
        <div className="payment-panel-error-wrapper">
          {isMobile ? (
            <ErrorBlock status="default" title="支付错误" description={error} />
          ) : (
            <Alert
              className="payment-panel-alert"
              type="error"
              showIcon
              message="支付错误"
              description={error}
            />
          )}
        </div>
      )}

      <div className="payment-summary-card">
        <h1 className="payment-page-title">模拟支付</h1>
        <div className="payment-summary-row">
          <span className="payment-summary-label">订单号</span>
          <span className="payment-summary-value">{orderNo}</span>
        </div>
        <div className="payment-summary-row">
          <span className="payment-summary-label">票数</span>
          <span className="payment-summary-value">{ticketCount} 张</span>
        </div>
        {showTime && (
          <div className="payment-summary-row">
            <span className="payment-summary-label">开场时间</span>
            <span className="payment-summary-value">{showTime}</span>
          </div>
        )}
        <div className="payment-summary-row">
          <span className="payment-summary-label">应付金额</span>
          <span className="payment-summary-amount">¥ {totalAmount}</span>
        </div>
        {status === 'NORMAL' && (
          <div className="payment-countdown-banner">
            {paymentDeadlineText ? (
              <>
                支付剩余时间：<span className="countdown-time">{paymentDeadlineText}</span>
              </>
            ) : (
              <span>支付期限以订单信息为准</span>
            )}
          </div>
        )}
      </div>

      {status === 'RESULT_UNKNOWN' ? (
        <div className="payment-unknown-container">
          {isMobile ? (
            <ErrorBlock
              status="default"
              title="支付结果未知"
              description="模拟支付请求已提交，但尚未收到网关确认。为防冲突和重复支付，禁止再次发起支付，请重新查询确认当前结果。"
            />
          ) : (
            <Alert
              type="warning"
              showIcon
              message="支付结果未知"
              description="模拟支付请求已提交，但尚未收到网关确认。为防冲突和重复支付，禁止再次发起支付，请重新查询确认当前结果。"
            />
          )}
          <div className="payment-actions">
            {isMobile ? (
              <MobileButton
                color="primary"
                className="payment-query-btn"
                onClick={onQueryOrderResult}
              >
                重新查询订单结果
              </MobileButton>
            ) : (
              <Button type="primary" className="payment-query-btn" onClick={onQueryOrderResult}>
                重新查询订单结果
              </Button>
            )}
          </div>
        </div>
      ) : status === 'PROCESSING' ? (
        <div className="payment-processing-container">
          {isMobile ? (
            <div className="mobile-loading-wrapper">
              <SpinLoading color="primary" />
              <span>网关受理中...</span>
            </div>
          ) : (
            <>
              <Spin tip="正在处理中..." />
              <p className="payment-processing-tip">网关受理中...</p>
            </>
          )}
        </div>
      ) : status === 'SUCCESS' ? (
        <div className="payment-success-container">
          <Alert type="success" showIcon message="支付成功" description="模拟支付已成功完成！" />
        </div>
      ) : (
        <div className="payment-password-section">
          <label className="payment-password-label" htmlFor="mock-payment-password">
            六位模拟支付密码：
          </label>
          <Input.Password
            id="mock-payment-password"
            className="payment-password-input"
            value={password}
            maxLength={6}
            inputMode="numeric"
            autoComplete="off"
            status={passwordError ? 'error' : undefined}
            disabled={isOfflineReadOnly || status !== 'NORMAL'}
            onChange={(event) => {
              setPassword(event.target.value.replace(/\D/g, '').slice(0, 6));
              setPasswordError(undefined);
            }}
            aria-label="六位模拟支付密码"
          />
          {passwordError && <div className="payment-password-error">{passwordError}</div>}
          <div className="payment-actions">
            {isMobile ? (
              <MobileButton
                color="primary"
                disabled={isOfflineReadOnly || status !== 'NORMAL'}
                onClick={handlePay}
              >
                确认支付
              </MobileButton>
            ) : (
              <Button
                type="primary"
                disabled={isOfflineReadOnly || status !== 'NORMAL'}
                onClick={handlePay}
              >
                确认支付
              </Button>
            )}
            {onCancelPayment &&
              (isMobile ? (
                <MobileButton onClick={onCancelPayment} className="payment-cancel-btn">
                  取消
                </MobileButton>
              ) : (
                <Button onClick={onCancelPayment} className="payment-cancel-btn">
                  取消
                </Button>
              ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default PaymentPanel;
