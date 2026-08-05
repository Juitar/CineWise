import React from 'react';
import { Button, Alert, Spin, Tag, Descriptions } from 'antd';
import type { OrderStatus } from '../order-list/OrderList';
import './index.css';

export interface OrderDetailProps {
  orderNo: string;
  status: OrderStatus;
  showTitle?: string;
  showTime?: string;
  cinemaName?: string;
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
  showTime = '待定',
  cinemaName = '未知影院',
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
  if (loading) {
    return (
      <div className="order-detail-container">
        <Spin tip="正在读取订单详细信息..." />
      </div>
    );
  }

  if (error) {
    return (
      <div className="order-detail-container">
        <Alert type="error" showIcon message="加载订单详情失败" description={error} />
      </div>
    );
  }

  const renderStatusBadge = () => {
    switch (status) {
      case 'PENDING_PAYMENT':
      case 'PAYING':
        return <Tag color="warning">待支付</Tag>;
      case 'PAID':
        return <Tag color="success">已出票</Tag>;
      case 'REFUNDING':
        return <Tag color="processing">退款处理中</Tag>;
      case 'REFUNDED':
        return <Tag color="default">已退款</Tag>;
      case 'CANCELLED':
        return <Tag color="default">已取消</Tag>;
      case 'EXPIRED':
        return <Tag color="error">已过期</Tag>;
      default:
        return <Tag>{status}</Tag>;
    }
  };

  const renderActions = () => {
    if (isOfflineReadOnly) {
      return (
        <div className="order-detail-actions">
          <Button onClick={onBackToHome}>返回首页</Button>
        </div>
      );
    }

    if (cancelResultUnknown) {
      return (
        <div className="order-detail-actions">
          <Alert
            type="warning"
            showIcon
            message="取消结果尚未确认"
            description="禁止再次取消，请查询当前订单状态确认服务端结果。"
          />
          <Button type="primary" onClick={onRecoverCancel} className="order-detail-btn">
            重新查询订单状态
          </Button>
        </div>
      );
    }

    switch (status) {
      case 'PENDING_PAYMENT':
        return (
          <div className="order-detail-actions">
            <Button type="primary" onClick={onPay} className="order-detail-btn">
              去支付
            </Button>
            <Button danger onClick={onCancel} className="order-detail-btn">
              取消订单
            </Button>
          </div>
        );

      case 'PAYING':
        return (
          <div className="order-detail-actions">
            <Alert
              type="info"
              showIcon
              message="支付结果确认中"
              description="请前往支付结果页查询，不要重复支付或取消订单。"
            />
            <Button type="primary" onClick={onPay} className="order-detail-btn">
              查询支付结果
            </Button>
          </div>
        );

      case 'PAID':
        return (
          <div className="order-detail-actions">
            <Button type="primary" onClick={onViewTicket} className="order-detail-btn">
              查看电子票
            </Button>
            <Button onClick={onApplyRefund} className="order-detail-btn">
              申请退票
            </Button>
          </div>
        );

      case 'REFUNDING':
        return (
          <div className="order-detail-actions">
            <Button onClick={onApplyRefund} className="order-detail-btn">
              查看退款进度
            </Button>
          </div>
        );

      case 'REFUNDED':
      case 'CANCELLED':
      case 'EXPIRED':
      default:
        return (
          <div className="order-detail-actions">
            <Button onClick={onBackToHome} className="order-detail-btn">
              返回首页
            </Button>
          </div>
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
          <div className="order-movie-name">{showTitle}</div>
          <div className="order-movie-meta">
            <div>影院：{cinemaName}</div>
            <div>开场时间：{showTime}</div>
            <div>
              座位：
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
