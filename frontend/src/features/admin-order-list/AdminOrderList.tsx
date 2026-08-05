import { Button, Pagination, Table } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import {
  renderOrderStatus,
  renderPaymentStatus,
  renderRefundStatus,
  renderTicketStatus,
} from '../admin-order/components/AdminOrderStatus';
import type { AdminOrderSummaryView } from '../admin-order/types';
import './index.css';

export interface AdminOrderListProps {
  orders: AdminOrderSummaryView[];
  total: number;
  page: number;
  size: number;
  loading?: boolean;
  onPageChange: (page: number, size: number) => void;
  onViewDetail: (orderNo: string) => void;
}

const renderTime = (timeStr: string | null | undefined) => {
  if (timeStr === null || timeStr === undefined || timeStr === '') return '-';
  return dayjs(timeStr).format('YYYY-MM-DD HH:mm:ss');
};

/**
 * 管理订单列表展示组件
 *
 * - 根据屏幕宽度自适应显示为 Table 或卡片列表
 * - 处理脱敏邮箱不存在（显示“用户信息不可用”）
 * - 处理子状态为 null 的情况（显示“未产生”）
 */
export const AdminOrderList: React.FC<AdminOrderListProps> = ({
  orders,
  total,
  page,
  size,
  loading = false,
  onPageChange,
  onViewDetail,
}) => {
  const columns = [
    {
      title: '订单号',
      dataIndex: 'orderNo',
      key: 'orderNo',
    },
    {
      title: '用户信息',
      dataIndex: 'emailMasked',
      key: 'emailMasked',
      render: (emailMasked: string | null) =>
        emailMasked !== null && emailMasked !== undefined ? (
          emailMasked
        ) : (
          <span className="admin-order-list-email-unavailable">用户信息不可用</span>
        ),
    },
    {
      title: '影片ID',
      dataIndex: 'movieId',
      key: 'movieId',
    },
    {
      title: '场次ID',
      dataIndex: 'showId',
      key: 'showId',
    },
    {
      title: '金额',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      render: (amount: string) => `¥ ${amount}`,
    },
    {
      title: '主状态',
      dataIndex: 'status', // 修改这里以适配可能的数据结构，实际上可能是 status
      key: 'status',
      render: (_: unknown, record: AdminOrderSummaryView) => renderOrderStatus(record.orderStatus),
    },
    {
      title: '支付/票/退款状态',
      key: 'subStatus',
      render: (_: unknown, record: AdminOrderSummaryView) => (
        <div className="admin-order-list-substatus-container">
          <div>支付: {renderPaymentStatus(record.paymentStatus)}</div>
          <div>电子票: {renderTicketStatus(record.ticketStatus)}</div>
          <div>退款: {renderRefundStatus(record.refundStatus)}</div>
        </div>
      ),
    },
    {
      title: '下单时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: renderTime,
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: AdminOrderSummaryView) => (
        <Button type="link" size="small" onClick={() => onViewDetail(record.orderNo)}>
          查看详情
        </Button>
      ),
    },
  ];

  return (
    <div className="admin-order-list-container">
      {/* PC 端表格 */}
      <div className="admin-order-list-table">
        <Table
          rowKey="orderId"
          columns={columns}
          dataSource={orders}
          pagination={false}
          loading={loading}
          locale={{ emptyText: '暂无符合条件的订单' }}
        />
      </div>

      {/* 移动端卡片 */}
      <div className="admin-order-list-cards">
        {orders.length === 0 && !loading && (
          <div className="admin-order-list-empty">暂无符合条件的订单</div>
        )}
        {orders.map((order) => (
          <article className="admin-order-card" key={order.orderId}>
            <div className="admin-order-card-header">
              <span className="admin-order-card-no">订单号: {order.orderNo}</span>
              {renderOrderStatus(order.orderStatus)}
            </div>
            <div className="admin-order-card-row">
              <span className="admin-order-card-label">用户信息</span>
              <span className="admin-order-card-value">
                {order.emailMasked !== null && order.emailMasked !== undefined ? (
                  order.emailMasked
                ) : (
                  <span className="admin-order-list-email-unavailable">用户信息不可用</span>
                )}
              </span>
            </div>
            <div className="admin-order-card-row">
              <span className="admin-order-card-label">影片/场次</span>
              <span className="admin-order-card-value">
                {order.movieId} / {order.showId}
              </span>
            </div>
            <div className="admin-order-card-row">
              <span className="admin-order-card-label">总金额</span>
              <span className="admin-order-card-value">¥ {order.totalAmount}</span>
            </div>
            <div className="admin-order-card-row">
              <span className="admin-order-card-label">支付状态</span>
              <span className="admin-order-card-value">
                {renderPaymentStatus(order.paymentStatus)}
              </span>
            </div>
            <div className="admin-order-card-row">
              <span className="admin-order-card-label">下单时间</span>
              <span className="admin-order-card-value">{renderTime(order.createdAt)}</span>
            </div>
            <div className="admin-order-card-actions">
              <Button type="primary" ghost size="small" onClick={() => onViewDetail(order.orderNo)}>
                查看详情
              </Button>
            </div>
          </article>
        ))}
      </div>

      <div className="admin-order-pagination">
        <Pagination
          current={page}
          pageSize={size}
          total={total}
          showSizeChanger
          showTotal={(totalCount) => `共 ${totalCount} 条`}
          onChange={onPageChange}
          disabled={loading}
        />
      </div>
    </div>
  );
};
