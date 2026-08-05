import React from 'react';
import { Spin, Alert, Empty, Tabs, Pagination, Button, Tag, Input } from 'antd';
import './index.css';

export type OrderStatus =
  'PENDING_PAYMENT' | 'PAYING' | 'PAID' | 'CANCELLED' | 'EXPIRED' | 'REFUNDING' | 'REFUNDED';

export interface OrderSummaryItem {
  orderId: string;
  orderNo: string;
  showTitle: string;
  showTime: string;
  ticketCount: number;
  totalAmount: string;
  status: OrderStatus;
  expireTime?: string;
  cinemaName?: string;
}

export interface OrderListProps {
  orders: OrderSummaryItem[];
  loading?: boolean;
  error?: string;
  isOfflineReadOnly?: boolean;
  selectedStatus?: string;
  selectedDate?: string;
  currentPage?: number;
  totalCount?: number;
  onStatusChange?: (status: string) => void;
  onDateFilterChange?: (date: string) => void;
  onPageChange?: (page: number) => void;
  onOrderClick?: (orderNo: string) => void;
}

/**
 * 个人订单列表展示组件
 * 支持状态筛选、日期筛选、订单卡片列表、分页，以及 Loading/Empty/Error/Normal 状态
 * 纯展示组件，不直接发送网络查询请求
 */
export const OrderList: React.FC<OrderListProps> = ({
  orders,
  loading = false,
  error,
  isOfflineReadOnly = false,
  selectedStatus = 'ALL',
  selectedDate = '',
  currentPage = 1,
  totalCount = 0,
  onStatusChange,
  onDateFilterChange,
  onPageChange,
  onOrderClick,
}) => {
  const statusTabs = [
    { key: 'ALL', label: '全部订单' },
    { key: 'PENDING_PAYMENT', label: '待支付' },
    { key: 'PAID', label: '已出票' },
    { key: 'REFUNDING', label: '退票中' },
    { key: 'REFUNDED', label: '已退票' },
    { key: 'CANCELLED', label: '已取消' },
  ];

  const renderStatusTag = (status: OrderStatus) => {
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

  const handleDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onDateFilterChange?.(e.target.value);
  };

  return (
    <div className="order-list-container">
      {isOfflineReadOnly && (
        <Alert
          className="order-list-alert"
          type="info"
          showIcon
          message="离线只读提示"
          description="当前处于离线状态，只呈现本地缓存的订单历史记录。"
        />
      )}

      <div className="order-list-header">
        <h1 className="order-list-title">我的订单</h1>
        <div className="order-list-filters">
          <label htmlFor="order-date-filter" className="order-date-label">
            日期筛选：
          </label>
          <Input
            id="order-date-filter"
            type="date"
            className="order-date-input"
            value={selectedDate}
            onChange={handleDateChange}
            aria-label="按开场日期筛选订单"
          />
        </div>
      </div>

      <div className="order-list-tabs">
        <Tabs
          activeKey={selectedStatus}
          onChange={(key) => onStatusChange?.(key)}
          items={statusTabs}
        />
      </div>

      {loading ? (
        <div className="order-list-loading">
          <Spin tip="正在载入订单列表..." />
        </div>
      ) : error ? (
        <div className="order-list-error">
          <Alert type="error" showIcon message="加载失败" description={error} />
        </div>
      ) : orders.length === 0 ? (
        <div className="order-list-empty">
          <Empty description="暂无符合条件的购票订单" />
        </div>
      ) : (
        <div className="order-card-list" role="list">
          {orders.map((item) => (
            <article key={item.orderId} className="order-card-item">
              <div className="order-card-header">
                <span className="order-card-no">订单号：{item.orderNo}</span>
                <div className="order-card-status">{renderStatusTag(item.status)}</div>
              </div>

              <div className="order-card-body">
                <div className="order-card-movie">{item.showTitle}</div>
                <div className="order-card-info">
                  <span>场次：{item.showTime}</span>
                  {item.cinemaName && <span>影院：{item.cinemaName}</span>}
                </div>
              </div>

              <div className="order-card-footer">
                <div className="order-card-price-area">
                  <span className="order-card-count">共 {item.ticketCount} 张</span>
                  <span className="order-card-total">实付款 ¥ {item.totalAmount}</span>
                </div>
                <div className="order-card-actions">
                  <Button
                    type="link"
                    className="order-card-btn"
                    onClick={() => onOrderClick?.(item.orderNo)}
                  >
                    查看详情
                  </Button>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      {!loading && !error && orders.length > 0 && (
        <div className="order-list-pagination">
          <Pagination
            current={currentPage}
            total={totalCount}
            pageSize={10}
            onChange={(page) => onPageChange?.(page)}
            showSizeChanger={false}
          />
        </div>
      )}
    </div>
  );
};

export default OrderList;
