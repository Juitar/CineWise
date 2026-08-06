import React, { useState } from 'react';
import { Button, Alert, Spin, Input, Checkbox } from 'antd';
import { Button as MobileButton, ErrorBlock, SpinLoading } from 'antd-mobile';
import {
  ELECTRONIC_TICKET_STATUS_LABELS,
  ORDER_STATUS_LABELS,
  REFUND_STATUS_LABELS,
} from '../../modules/order/status-presentation';
import type { ElectronicTicketStatus, OrderStatus } from '../../modules/order/types';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

export type RefundVisualStatus =
  | 'NORMAL'
  | 'REQUESTED'
  | 'PROCESSING'
  | 'SUCCESS'
  | 'RESULT_UNKNOWN'
  | 'NOT_REFUNDABLE'
  | 'ERROR'
  | 'LOADING';

export interface RefundConfirmationProps {
  orderNo: string;
  refundAmount: string;
  orderStatus: OrderStatus;
  ticketStatus: ElectronicTicketStatus;
  showStartTime: string;
  impactText: string;
  status?: RefundVisualStatus;
  error?: string;
  isOfflineReadOnly?: boolean;
  onConfirmRefund?: (reason?: string) => void;
  onQueryRefundResult?: () => void;
  onCancel?: () => void;
}

/**
 * 退票申请与二次确认展示组件
 * 展示退款金额、订单状态、电子票状态、开场时间和退票影响文案
 * 退款原因选填，确认提交仅要求勾选二次确认复选框
 */
export const RefundConfirmation: React.FC<RefundConfirmationProps> = ({
  orderNo,
  refundAmount,
  orderStatus,
  ticketStatus,
  showStartTime,
  impactText,
  status = 'NORMAL',
  error,
  isOfflineReadOnly = false,
  onConfirmRefund,
  onQueryRefundResult,
  onCancel,
}) => {
  const [reason, setReason] = useState<string>('');
  const [confirmed, setConfirmed] = useState<boolean>(false);
  const isMobile = useMediaQuery('(max-width: 1023px)');

  if (status === 'LOADING') {
    return (
      <div className="refund-container">
        {isMobile ? (
          <div className="mobile-loading-wrapper">
            <SpinLoading color="primary" />
            <span>正在处理退款申请...</span>
          </div>
        ) : (
          <Spin tip="正在处理退款申请..." />
        )}
      </div>
    );
  }

  if (status === 'NOT_REFUNDABLE') {
    return (
      <div className="refund-container">
        <div className="refund-header">
          <h1 className="refund-title">申请退票</h1>
          <div className="refund-orderno">关联订单号：{orderNo}</div>
        </div>
        <Alert
          className="refund-alert"
          type="warning"
          showIcon
          message="当前订单不可退票"
          description={error || '该订单当前不满足退票条件，场次开始后不可退票。'}
        />
        <div className="refund-actions">
          {isMobile ? (
            <MobileButton onClick={onCancel} className="refund-btn">
              返回订单详情
            </MobileButton>
          ) : (
            <Button onClick={onCancel} className="refund-btn">
              返回订单详情
            </Button>
          )}
        </div>
      </div>
    );
  }

  const handleSubmit = () => {
    if (confirmed) {
      onConfirmRefund?.(reason.trim() || undefined);
    }
  };

  return (
    <div className="refund-container">
      {isOfflineReadOnly && (
        <Alert
          className="refund-alert"
          type="info"
          showIcon
          message="离线只读提示"
          description="当前处于离线状态，不可进行退票表单提交。"
        />
      )}

      {error && (
        <div className="refund-alert-wrapper">
          {isMobile ? (
            <ErrorBlock status="default" title="退票处理异常" description={error} />
          ) : (
            <Alert
              className="refund-alert"
              type="error"
              showIcon
              message="退票处理异常"
              description={error}
            />
          )}
        </div>
      )}

      <div className="refund-header">
        <h1 className="refund-title">申请退票</h1>
        <div className="refund-orderno">关联订单号：{orderNo}</div>
      </div>

      <div className="refund-summary-card">
        <div className="refund-summary-row">
          <span className="refund-summary-label">预计退款金额</span>
          <span className="refund-amount">¥ {refundAmount}</span>
        </div>
        <div className="refund-summary-row">
          <span className="refund-summary-label">当前订单状态</span>
          <span className="refund-summary-value">{ORDER_STATUS_LABELS[orderStatus]}</span>
        </div>
        <div className="refund-summary-row">
          <span className="refund-summary-label">电子票状态</span>
          <span className="refund-summary-value">
            {ELECTRONIC_TICKET_STATUS_LABELS[ticketStatus]}
          </span>
        </div>
        <div className="refund-summary-row">
          <span className="refund-summary-label">放映开场时间</span>
          <span className="refund-summary-value">{showStartTime}</span>
        </div>

        <div className="refund-impact-box">
          <div className="refund-impact-title">退票规则与影响说明：</div>
          <p className="refund-impact-text">{impactText}</p>
        </div>
      </div>

      {status === 'RESULT_UNKNOWN' ? (
        <div className="refund-state-container">
          {isMobile ? (
            <ErrorBlock
              status="default"
              title="退款状态未知"
              description="已向后端提交退票请求，但尚未确认最终退款结果。为防止重复扣除或状态冲突，禁止再次退票，请重新查询确认当前结果。"
            />
          ) : (
            <Alert
              type="warning"
              showIcon
              message="退款状态未知"
              description="已向后端提交退票请求，但尚未确认最终退款结果。为防止重复扣除或状态冲突，禁止再次退票，请重新查询确认当前结果。"
            />
          )}
          <div className="refund-actions">
            {isMobile ? (
              <MobileButton color="primary" onClick={onQueryRefundResult} className="refund-btn">
                重新查询退款结果
              </MobileButton>
            ) : (
              <Button type="primary" onClick={onQueryRefundResult} className="refund-btn">
                重新查询退款结果
              </Button>
            )}
          </div>
        </div>
      ) : status === 'PROCESSING' || status === 'REQUESTED' ? (
        <div className="refund-state-container">
          {isMobile ? (
            <SpinLoading color="primary" className="mobile-spin-large" />
          ) : (
            <Spin size="large" />
          )}
          <p className="refund-processing-tip">
            {status === 'REQUESTED'
              ? `退款${REFUND_STATUS_LABELS.REQUESTED}...`
              : `退款${REFUND_STATUS_LABELS.PROCESSING}...`}
          </p>
        </div>
      ) : status === 'SUCCESS' ? (
        <div className="refund-state-container">
          <Alert
            type="success"
            showIcon
            message={REFUND_STATUS_LABELS.SUCCESS}
            description={`退款 ¥ ${refundAmount} 申请成功。`}
          />
        </div>
      ) : (
        <div className="refund-form-section">
          <div className="refund-field">
            <label htmlFor="refund-reason-input" className="refund-label">
              退票原因（选填）：
            </label>
            <Input.TextArea
              id="refund-reason-input"
              rows={3}
              placeholder="请输入申请退票的具体原因（选填）..."
              disabled={isOfflineReadOnly || status !== 'NORMAL'}
              aria-label="退票原因输入"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>

          <div className="refund-confirmation-area">
            <Checkbox
              id="refund-confirm-checkbox"
              disabled={isOfflineReadOnly || status !== 'NORMAL'}
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
            >
              我已仔细阅读上述退票规则及说明，并确认申请退费（二次确认）
            </Checkbox>
          </div>

          <div className="refund-actions">
            {isMobile ? (
              <MobileButton
                color="danger"
                disabled={!confirmed || isOfflineReadOnly || status !== 'NORMAL'}
                onClick={handleSubmit}
                className="refund-btn"
              >
                确认申请退票
              </MobileButton>
            ) : (
              <Button
                type="primary"
                danger
                disabled={!confirmed || isOfflineReadOnly || status !== 'NORMAL'}
                onClick={handleSubmit}
                className="refund-btn"
              >
                确认申请退票
              </Button>
            )}
            {isMobile ? (
              <MobileButton onClick={onCancel} className="refund-btn">
                暂不退票
              </MobileButton>
            ) : (
              <Button onClick={onCancel} className="refund-btn">
                暂不退票
              </Button>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default RefundConfirmation;
