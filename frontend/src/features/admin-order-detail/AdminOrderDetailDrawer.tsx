import { Button, Drawer, Spin, Table } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import {
  renderOrderStatus,
  renderPaymentStatus,
  renderRefundStatus,
  renderTicketStatus,
} from '../admin-order/components/AdminOrderStatus';
import type { AdminOrderDetailView } from '../admin-order/types';
import './index.css';

export interface AdminOrderDetailDrawerProps {
  open: boolean;
  onClose: () => void;
  detail: AdminOrderDetailView | null;
  loading?: boolean;
  errorMessage?: string;
  traceId?: string;
  canRetry?: boolean;
  onRetry?: () => void;
}

const renderTime = (timeStr: string | null | undefined) => {
  if (timeStr === null || timeStr === undefined || timeStr === '') {
    return <span className="admin-order-detail-empty-text">未产生</span>;
  }
  return dayjs(timeStr).format('YYYY-MM-DD HH:mm:ss');
};

const DataItem = ({ label, value }: { label: string; value: React.ReactNode }) => (
  <div className="admin-order-detail-item">
    <div className="admin-order-detail-label">{label}</div>
    <div className="admin-order-detail-value">
      {value !== null && value !== undefined && value !== '' ? (
        value
      ) : (
        <span className="admin-order-detail-empty-text">-</span>
      )}
    </div>
  </div>
);

/**
 * 管理订单只读详情抽屉组件
 *
 * - 严格只读，不提供退款、重试等管理操作
 * - 安全性：不展示密码、jwt、qrPayload、idempotencyKey 等内部信息
 */
export const AdminOrderDetailDrawer: React.FC<AdminOrderDetailDrawerProps> = ({
  open,
  onClose,
  detail,
  loading = false,
  errorMessage,
  traceId,
  canRetry = false,
  onRetry,
}) => {
  if (!detail && !loading) {
    return (
      <Drawer title="订单详情" open={open} onClose={onClose} width={720}>
        <div className="admin-order-detail-empty-text admin-order-detail-error-empty">
          <div>{errorMessage ?? '请选择订单查看详情'}</div>
          {traceId ? <div className="admin-order-detail-trace">TraceId: {traceId}</div> : null}
          {canRetry && onRetry ? (
            <Button className="admin-order-detail-retry" onClick={onRetry} type="primary">
              手动重试
            </Button>
          ) : null}
        </div>
      </Drawer>
    );
  }

  const seatColumns = [
    { title: '座位ID', dataIndex: 'seatId', key: 'seatId' },
    { title: '行号', dataIndex: 'rowNo', key: 'rowNo' },
    { title: '列号', dataIndex: 'seatNo', key: 'seatNo' },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      render: (val: string) => `¥ ${val}`,
    },
  ];

  return (
    <Drawer title="订单详情" open={open} onClose={onClose} width={720}>
      <Spin spinning={loading}>
        {detail && (
          <>
            <section className="admin-order-detail-section">
              <div className="admin-order-detail-title">基本信息</div>
              <div className="admin-order-detail-grid">
                <DataItem label="订单ID" value={detail.summary.orderId} />
                <DataItem label="订单号" value={detail.summary.orderNo} />
                <DataItem label="用户ID" value={detail.summary.userId} />
                <DataItem
                  label="用户信息"
                  value={
                    detail.summary.emailMasked !== null &&
                    detail.summary.emailMasked !== undefined ? (
                      detail.summary.emailMasked
                    ) : (
                      <span className="admin-order-list-email-unavailable">用户信息不可用</span>
                    )
                  }
                />
                <DataItem label="影片ID" value={detail.summary.movieId} />
                <DataItem label="场次ID" value={detail.summary.showId} />
                <DataItem label="影院ID" value={detail.summary.cinemaId} />
                <DataItem
                  label="主订单状态"
                  value={renderOrderStatus(detail.summary.orderStatus)}
                />
                <DataItem label="票数" value={detail.summary.ticketCount} />
                <DataItem label="总金额" value={`¥ ${detail.summary.totalAmount}`} />
                <DataItem label="订单版本" value={detail.summary.stateVersion} />
              </div>
            </section>

            <section className="admin-order-detail-section">
              <div className="admin-order-detail-title">订单时序</div>
              <div className="admin-order-detail-grid">
                <DataItem label="创建时间" value={renderTime(detail.summary.createdAt)} />
                <DataItem label="过期时间" value={renderTime(detail.summary.expireTime)} />
                <DataItem label="支付完成时间" value={renderTime(detail.paidTime)} />
                <DataItem label="取消时间" value={renderTime(detail.cancelledTime)} />
                <DataItem label="退款时间" value={renderTime(detail.refundedTime)} />
                <DataItem label="最后更新" value={renderTime(detail.summary.updatedAt)} />
              </div>
            </section>

            <section className="admin-order-detail-section">
              <div className="admin-order-detail-title">座位明细快照</div>
              <Table
                className="admin-order-detail-seats"
                columns={seatColumns}
                dataSource={detail.seats}
                rowKey="seatId"
                pagination={false}
                size="small"
                bordered
              />
            </section>

            <section className="admin-order-detail-section">
              <div className="admin-order-detail-title">交易状态快照</div>
              <div className="admin-order-detail-grid">
                {/* 支付摘要 */}
                <div className="admin-order-detail-summary-section">
                  <h4 className="admin-order-detail-summary-title">支付摘要</h4>
                  {detail.payment ? (
                    <div className="admin-order-detail-grid">
                      <DataItem label="支付单号" value={detail.payment.paymentNo} />
                      <DataItem label="状态" value={renderPaymentStatus(detail.payment.status)} />
                      <DataItem label="金额" value={`¥ ${detail.payment.amount}`} />
                      <DataItem label="发起时间" value={renderTime(detail.payment.requestedAt)} />
                      <DataItem label="支付时间" value={renderTime(detail.payment.paidAt)} />
                    </div>
                  ) : (
                    <div className="admin-order-detail-empty-text">无支付记录</div>
                  )}
                </div>

                {/* 电子票摘要 */}
                <div className="admin-order-detail-summary-section-mt">
                  <h4 className="admin-order-detail-summary-title">电子票摘要</h4>
                  {detail.ticket ? (
                    <div className="admin-order-detail-grid">
                      <DataItem label="电子票号" value={detail.ticket.ticketCode} />
                      <DataItem label="状态" value={renderTicketStatus(detail.ticket.status)} />
                      <DataItem label="出票时间" value={renderTime(detail.ticket.issuedAt)} />
                      <DataItem label="失效时间" value={renderTime(detail.ticket.invalidatedAt)} />
                    </div>
                  ) : (
                    <div className="admin-order-detail-empty-text">无电子票记录</div>
                  )}
                </div>

                {/* 退款摘要 */}
                <div className="admin-order-detail-summary-section-mt">
                  <h4 className="admin-order-detail-summary-title">退款摘要</h4>
                  {detail.refund ? (
                    <div className="admin-order-detail-grid">
                      <DataItem label="退款单号" value={detail.refund.refundNo} />
                      <DataItem label="状态" value={renderRefundStatus(detail.refund.status)} />
                      <DataItem label="退款原因" value={detail.refund.reason ?? '未填写'} />
                      <DataItem label="发起时间" value={renderTime(detail.refund.requestedAt)} />
                      <DataItem label="完成时间" value={renderTime(detail.refund.processedAt)} />
                    </div>
                  ) : (
                    <div className="admin-order-detail-empty-text">无退款记录</div>
                  )}
                </div>
              </div>
            </section>
          </>
        )}
      </Spin>
    </Drawer>
  );
};
