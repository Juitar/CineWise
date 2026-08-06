import React from 'react';
import { Button, Alert, Spin, Tag, Descriptions } from 'antd';
import { Button as MobileButton, ErrorBlock, SpinLoading } from 'antd-mobile';
import { ORDER_STATUS_LABELS } from '../../modules/order/status-presentation';
import type { OrderStatus } from '../../modules/order/types';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

export interface OrderDetailProps {
  orderNo: string;
  status: OrderStatus;
  showTitle?: string;
  showId?: string;
  showTime?: string;
  cinemaName?: string;
  cinemaArea?: string;
  cinemaAddress?: string;
  posterUrl?: string | null;
  seatLabels?: string[];
  ticketCount: number;
  unitPrice: string;
  totalAmount: string;
  expireTime?: string;
  updatedAt?: string;
  loading?: boolean;
  error?: string;
  isOfflineReadOnly?: boolean;
  onPay?: () => void;
  onCancel?: () => void;
  onViewTicket?: () => void;
  onApplyRefund?: () => void;
  onBackToHome?: () => void;
  cancelResultUnknown?: boolean;
  onRecoverCancel?: () => void;
}

/**
 * 个人订单详情展示组件
 * 根据订单状态展示对“去支付”“取消订单”“查看电子票”“申请退票”等按钮
 * 按钮只调用传入的 callback，不执行任何业务逻辑或请求
 */
export const OrderDetail: React.FC<OrderDetailProps> = ({
  orderNo,
  status,
  showTitle = '未知影片',
  showId,
  showTime = '待定',
  cinemaName = '未知影院',
  cinemaArea,
  cinemaAddress,
  posterUrl,
  seatLabels = [],
  ticketCount,
  unitPrice,
  totalAmount,
  expireTime,
  updatedAt,
  loading = false,
  error,
  isOfflineReadOnly = false,
  onPay,
  onCancel,
  onViewTicket,
  onApplyRefund,
  onBackToHome,
  cancelResultUnknown = false,
  onRecoverCancel,
}) => {
  const isMobile = useMediaQuery('(max-width: 1023px)');
  if (loading) {
    return (
      <div className="order-detail-container">
        {isMobile ? (
          <div className="mobile-loading-wrapper">
            <SpinLoading color="primary" />
            <span>正在读取订单详细信息...</span>
          </div>
        ) : (
          <Spin tip="正在读取订单详细信息..." />
        )}
      </div>
    );
  }

  if (error) {
    return (
      <div className="order-detail-container">
        {isMobile ? (
          <ErrorBlock status="default" title="加载订单详情失败" description={error} />
        ) : (
          <Alert type="error" showIcon message="加载订单详情失败" description={error} />
        )}
      </div>
    );
  }

  const renderStatusBadge = () => {
    switch (status) {
      case 'PENDING_PAYMENT':
      case 'PAYING':
        return <Tag color="warning">{ORDER_STATUS_LABELS[status]}</Tag>;
      case 'PAID':
        return <Tag color="success">{ORDER_STATUS_LABELS[status]}</Tag>;
      case 'REFUNDING':
        return <Tag color="processing">{ORDER_STATUS_LABELS[status]}</Tag>;
      case 'REFUNDED':
        return <Tag color="default">{ORDER_STATUS_LABELS[status]}</Tag>;
      case 'CANCELLED':
        return <Tag color="default">{ORDER_STATUS_LABELS[status]}</Tag>;
      case 'EXPIRED':
        return <Tag color="error">{ORDER_STATUS_LABELS[status]}</Tag>;
    }
  };

  const renderActions = () => {
    const renderPrimaryBtn = (text: string, onClick?: () => void, danger?: boolean) => {
      return isMobile ? (
        <MobileButton
          color={danger ? 'danger' : 'primary'}
          className="order-detail-btn"
          onClick={onClick}
        >
          {text}
        </MobileButton>
      ) : (
        <Button type="primary" danger={danger} className="order-detail-btn" onClick={onClick}>
          {text}
        </Button>
      );
    };

    const renderDefaultBtn = (text: string, onClick?: () => void, danger?: boolean) => {
      return isMobile ? (
        <MobileButton
          color={danger ? 'danger' : 'default'}
          className="order-detail-btn"
          onClick={onClick}
        >
          {text}
        </MobileButton>
      ) : (
        <Button danger={danger} className="order-detail-btn" onClick={onClick}>
          {text}
        </Button>
      );
    };

    if (isOfflineReadOnly) {
      return (
        <div className="order-detail-actions">{renderDefaultBtn('返回首页', onBackToHome)}</div>
      );
    }

    if (cancelResultUnknown) {
      return (
        <div className="order-detail-actions">
          {isMobile ? (
            <ErrorBlock
              status="default"
              title="取消结果尚未确认"
              description="禁止再次取消，请查询当前订单状态确认服务端结果。"
            />
          ) : (
            <Alert
              type="warning"
              showIcon
              message="取消结果尚未确认"
              description="禁止再次取消，请查询当前订单状态确认服务端结果。"
            />
          )}
          {renderPrimaryBtn('重新查询订单状态', onRecoverCancel)}
        </div>
      );
    }

    switch (status) {
      case 'PENDING_PAYMENT':
        return (
          <div className="order-detail-actions">
            {renderPrimaryBtn('去支付', onPay)}
            {renderDefaultBtn('取消订单', onCancel, true)}
          </div>
        );

      case 'PAYING':
        return (
          <div className="order-detail-actions">
            {isMobile ? (
              <ErrorBlock
                status="default"
                title="支付结果确认中"
                description="请前往支付结果页查询，不要重复支付或取消订单。"
              />
            ) : (
              <Alert
                type="info"
                showIcon
                message="支付结果确认中"
                description="请前往支付结果页查询，不要重复支付或取消订单。"
              />
            )}
            {renderPrimaryBtn('查询支付结果', onPay)}
          </div>
        );

      case 'PAID':
        return (
          <div className="order-detail-actions">
            {renderPrimaryBtn('查看电子票', onViewTicket)}
            {renderDefaultBtn('申请退票', onApplyRefund)}
          </div>
        );

      case 'REFUNDING':
        return (
          <div className="order-detail-actions">
            {renderDefaultBtn('查看退款进度', onApplyRefund)}
          </div>
        );

      case 'REFUNDED':
      case 'CANCELLED':
      case 'EXPIRED':
      default:
        return (
          <div className="order-detail-actions">{renderDefaultBtn('返回首页', onBackToHome)}</div>
        );
    }
  };

  return (
    <div className="order-detail-container">
      {isOfflineReadOnly && (
        <Alert
          className="order-detail-alert"
          type="info"
          showIcon
          message="离线只读提示"
          description="当前处于离线模式，只读展示订单信息。"
        />
      )}

      <div className="order-detail-header">
        <div className="order-detail-title-row">
          <h1 className="order-detail-title">订单详情</h1>
          {renderStatusBadge()}
        </div>
        <div className="order-detail-no">订单号：{orderNo}</div>
      </div>

      <div className="order-detail-section">
        <h2 className="order-section-title">影片与场次</h2>
        <div className="order-movie-card">
          {posterUrl && <img className="order-movie-poster" src={posterUrl} alt="" />}
          <div className="order-movie-name">{showTitle}</div>
          <div className="order-movie-meta">
            {showId && <div>场次编号：{showId}</div>}
            <div>影院：{cinemaName}</div>
            {cinemaArea && <div>区域：{cinemaArea}</div>}
            {cinemaAddress && <div>地址：{cinemaAddress}</div>}
            <div>开场时间：{showTime}</div>
            <div>
              座位编号：
              <span className="order-seat-list">
                {seatLabels.length > 0 ? seatLabels.join('、') : '见凭证座位'}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="order-detail-section">
        <h2 className="order-section-title">金额与票数</h2>
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="购票数量">{ticketCount} 张</Descriptions.Item>
          <Descriptions.Item label="单价">¥ {unitPrice}</Descriptions.Item>
          <Descriptions.Item label="订单总金额">
            <span className="order-amount-highlight">¥ {totalAmount}</span>
          </Descriptions.Item>
          {expireTime && <Descriptions.Item label="支付截止时间">{expireTime}</Descriptions.Item>}
          {updatedAt && <Descriptions.Item label="最后更新时间">{updatedAt}</Descriptions.Item>}
        </Descriptions>
      </div>

      {renderActions()}
    </div>
  );
};

export default OrderDetail;
