import React from 'react';
import { Button, Alert, Spin } from 'antd';
import './index.css';

export type PaymentResultStatus =
  | 'LOADING'
  | 'PROCESSING'
  | 'CONFIRMING'
  | 'SUCCESS'
  | 'PENDING_PAYMENT'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'REFUNDED'
  | 'RESULT_UNKNOWN'
  | 'ERROR';

export interface PaymentResultProps {
  orderNo: string;
  amount: string;
  status: PaymentResultStatus;
  error?: string;
  isOfflineReadOnly?: boolean;
  onViewOrder?: () => void;
  onRetryQuery?: () => void;
  onRetryPay?: () => void;
  onBackToHome?: () => void;
}

/**
 * 支付结果展示组件
 * 纯展示组件，不包含轮询、定时器或接口请求，通过 props 切换状态
 */
export const PaymentResult: React.FC<PaymentResultProps> = ({
  orderNo,
  amount,
  status,
  error,
  isOfflineReadOnly = false,
  onViewOrder,
  onRetryQuery,
  onRetryPay,
  onBackToHome,
}) => {
  if (status === 'LOADING') {
    return (
      <div className="payment-result-container">
        <Spin tip="正在加载支付结果信息..." />
      </div>
    );
  }

  const renderStatusContent = () => {
    switch (status) {
      case 'SUCCESS':
        return (
          <div className="payment-result-state">
            <div className="payment-result-badge success">支付成功</div>
            <p className="payment-result-desc">
              订单已完成支付，金额 ¥ {amount}，您的电子票已生成。
            </p>
            <div className="payment-result-actions">
              <Button type="primary" onClick={onViewOrder} className="payment-result-btn">
                查看订单
              </Button>
              <Button onClick={onBackToHome} className="payment-result-btn">
                返回首页
              </Button>
            </div>
          </div>
        );

      case 'PROCESSING':
      case 'CONFIRMING':
        return (
          <div className="payment-result-state">
            <Spin size="large" />
            <div className="payment-result-badge processing">
              {status === 'PROCESSING' ? '支付处理中' : '结果确认中'}
            </div>
            <p className="payment-result-desc">银行或支付网关正在处理中，请勿重复发起支付。</p>
            <div className="payment-result-actions">
              <Button type="primary" onClick={onRetryQuery} className="payment-result-btn">
                刷新结果
              </Button>
              <Button onClick={onViewOrder} className="payment-result-btn">
                查看订单
              </Button>
            </div>
          </div>
        );

      case 'PENDING_PAYMENT':
        return (
          <div className="payment-result-state">
            <div className="payment-result-badge pending">订单仍待支付</div>
            <p className="payment-result-desc">
              该订单尚未完成支付，应付 ¥ {amount}，请尽快完成付款。
            </p>
            <div className="payment-result-actions">
              {!isOfflineReadOnly && (
                <Button type="primary" onClick={onRetryPay} className="payment-result-btn">
                  前往支付
                </Button>
              )}
              <Button onClick={onViewOrder} className="payment-result-btn">
                查看订单
              </Button>
            </div>
          </div>
        );

      case 'EXPIRED':
        return (
          <div className="payment-result-state">
            <div className="payment-result-badge expired">订单已过期</div>
            <p className="payment-result-desc">
              该订单未在有效时间内完成付款，系统已自动释放座位。
            </p>
            <div className="payment-result-actions">
              <Button type="primary" onClick={onBackToHome} className="payment-result-btn">
                返回首页
              </Button>
            </div>
          </div>
        );

      case 'CANCELLED':
        return (
          <div className="payment-result-state">
            <div className="payment-result-badge expired">订单已取消</div>
            <p className="payment-result-desc">该订单已取消，系统已释放原锁定座位。</p>
            <div className="payment-result-actions">
              <Button type="primary" onClick={onBackToHome} className="payment-result-btn">
                返回首页
              </Button>
            </div>
          </div>
        );

      case 'REFUNDED':
        return (
          <div className="payment-result-state">
            <div className="payment-result-badge expired">订单已退款</div>
            <p className="payment-result-desc">该订单已完成退票，关联电子票已失效。</p>
            <div className="payment-result-actions">
              <Button type="primary" onClick={onViewOrder} className="payment-result-btn">
                查看订单
              </Button>
            </div>
          </div>
        );

      case 'RESULT_UNKNOWN':
        return (
          <div className="payment-result-state">
            <Alert
              type="warning"
              showIcon
              message="支付状态未知"
              description="网关响应超时或处理异常，为确保表单与资金安全，禁止再次支付，仅提供结果复查。"
            />
            <div className="payment-result-actions">
              <Button type="primary" onClick={onRetryQuery} className="payment-result-btn">
                重新查询订单结果
              </Button>
              <Button onClick={onViewOrder} className="payment-result-btn">
                查看订单
              </Button>
            </div>
          </div>
        );

      case 'ERROR':
      default:
        return (
          <div className="payment-result-state">
            <Alert
              type="error"
              showIcon
              message="支付异常"
              description={error || '在获取支付结果期间发生错误。'}
            />
            <div className="payment-result-actions">
              <Button onClick={onViewOrder} className="payment-result-btn">
                查看订单
              </Button>
            </div>
          </div>
        );
    }
  };

  return (
    <div className="payment-result-container">
      {isOfflineReadOnly && (
        <Alert
          className="payment-result-alert"
          type="info"
          showIcon
          message="离线只读提示"
          description="当前为离线或网络故障只读模式。"
        />
      )}

      <div className="payment-result-header">
        <h1 className="payment-result-title">支付结果</h1>
        <div className="payment-result-orderno">订单号：{orderNo}</div>
      </div>

      {renderStatusContent()}
    </div>
  );
};

export default PaymentResult;
