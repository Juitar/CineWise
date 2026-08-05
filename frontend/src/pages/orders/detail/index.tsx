import React from 'react';
import { history, useParams } from 'umi';
import { message } from 'antd';
import { OrderDetail } from '../../../features/order-detail/OrderDetail';
import {
  useCancelOrder,
  useOrder,
  usePaymentQuery,
} from '../../../modules/order/transaction-hooks';
import { formatOrderDateTime } from '../../../modules/order/formatters';
import './index.css';

/**
 * 个人订单详情页面：/orders/:orderNo
 * 取消响应未知时只刷新当前订单状态，不自动重发取消请求。
 */
export default function OrderDetailPage() {
  const { orderNo = '' } = useParams<{ orderNo: string }>();
  const orderQuery = useOrder(orderNo);
  const cancellation = useCancelOrder(orderNo);
  const paymentQuery = usePaymentQuery(orderNo);
  const order = orderQuery.data;

  const handleCancel = async () => {
    const cancelled = await cancellation.submit();
    if (cancelled) {
      await orderQuery.refresh();
      message.success('订单已取消');
      return;
    }
  };

  const recoverCancellation = async () => {
    const recovered = await cancellation.recover();
    if (recovered) {
      await orderQuery.refresh();
    }
  };

  const handleViewTicket = async () => {
    const payment = await paymentQuery.query();
    if (payment?.ticketId) {
      history.push(`/tickets/${encodeURIComponent(payment.ticketId)}`);
      return;
    }
    if (!paymentQuery.error) {
      message.error('暂时无法取得电子票信息，请稍后重试');
    }
  };

  return (
    <div className="order-detail-page-wrapper">
      <OrderDetail
        orderNo={order?.orderNo ?? orderNo}
        status={order?.status ?? 'PENDING_PAYMENT'}
        showTitle={order ? '影片信息暂不可用' : undefined}
        showId={order?.showId}
        showTime={formatOrderDateTime(order?.showStartTime)}
        seatLabels={order?.seatIds}
        ticketCount={order?.ticketCount ?? 0}
        unitPrice={order?.unitPrice ?? '0.00'}
        totalAmount={order?.totalAmount ?? '0.00'}
        expireTime={order?.expireTime}
        updatedAt={order?.updatedAt}
        loading={orderQuery.loading}
        error={
          orderQuery.error?.message ?? cancellation.error?.message ?? paymentQuery.error?.message
        }
        onPay={() =>
          history.push(
            order?.status === 'PAYING'
              ? `/payments/${encodeURIComponent(orderNo)}/result`
              : `/payments/${encodeURIComponent(orderNo)}`,
          )
        }
        onCancel={() => void handleCancel()}
        onViewTicket={() => void handleViewTicket()}
        onApplyRefund={() => history.push(`/orders/${encodeURIComponent(orderNo)}/refund`)}
        onBackToHome={() => history.push('/')}
        cancelResultUnknown={cancellation.resultUnknown}
        onRecoverCancel={() => void recoverCancellation()}
      />
    </div>
  );
}
