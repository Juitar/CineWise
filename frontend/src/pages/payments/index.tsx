import React from 'react';
import { history, useParams } from 'umi';
import { PaymentPanel } from '../../features/payment-panel/PaymentPanel';
import { useOrder, usePaymentAction } from '../../modules/order/transaction-hooks';
import { formatOrderDateTime } from '../../modules/order/formatters';
import { usePaymentDeadline } from '../../modules/order/payment-deadline';
import {
  ORDERS_BREADCRUMB_ITEM,
  PROFILE_BREADCRUMB_ITEM,
  TransactionBreadcrumb,
} from '../../features/transaction-breadcrumb/TransactionBreadcrumb';
import './index.css';

/**
 * 模拟支付页面：/payments/:orderNo
 * 六位 PIN 只在 PaymentPanel 内完成格式校验和清空，页面永远收不到该值。
 */
export default function PaymentPage() {
  const { orderNo = '' } = useParams<{ orderNo: string }>();
  const orderQuery = useOrder(orderNo);
  const paymentAction = usePaymentAction(orderNo);
  const isOrderNotPayable =
    orderQuery.data !== null &&
    orderQuery.data.status !== 'PENDING_PAYMENT' &&
    orderQuery.data.status !== 'PAYING' &&
    orderQuery.data.status !== 'PAID';
  const status =
    paymentAction.resultUnknown || orderQuery.data?.status === 'PAYING'
      ? 'RESULT_UNKNOWN'
      : paymentAction.submitting
        ? 'PROCESSING'
        : paymentAction.payment?.paymentStatus === 'SUCCESS' || orderQuery.data?.status === 'PAID'
          ? 'SUCCESS'
          : orderQuery.loading
            ? 'LOADING'
            : isOrderNotPayable
              ? 'ERROR'
              : 'NORMAL';
  const refreshExpiredOrder = () => {
    void orderQuery.refresh();
  };
  const paymentDeadline = usePaymentDeadline(
    orderQuery.data?.expireTime,
    status === 'NORMAL',
    refreshExpiredOrder,
  );

  const submitPayment = async () => {
    const payment = await paymentAction.submit();
    if (payment) {
      history.push(`/payments/${encodeURIComponent(orderNo)}/result`);
    }
  };

  const queryPayment = async () => {
    const payment = await paymentAction.query();
    if (payment) {
      history.push(`/payments/${encodeURIComponent(orderNo)}/result`);
    }
  };
  return (
    <div className="payment-page-wrapper">
      <div className="payment-page-content">
        <TransactionBreadcrumb
          items={[
            PROFILE_BREADCRUMB_ITEM,
            ORDERS_BREADCRUMB_ITEM,
            { label: '订单详情', to: `/orders/${encodeURIComponent(orderNo)}` },
            { label: '支付订单' },
          ]}
        />
        <PaymentPanel
          orderNo={orderQuery.data?.orderNo ?? orderNo}
          ticketCount={orderQuery.data?.ticketCount ?? 0}
          totalAmount={orderQuery.data?.totalAmount ?? '0.00'}
          showTime={formatOrderDateTime(orderQuery.data?.showStartTime)}
          paymentDeadlineText={paymentDeadline.text}
          status={status}
          error={
            paymentAction.error?.message ??
            orderQuery.error?.message ??
            (isOrderNotPayable ? '当前订单状态不可支付，请返回订单详情查看最新状态' : undefined)
          }
          onPay={() => void submitPayment()}
          onQueryOrderResult={() => void queryPayment()}
          onCancelPayment={() => history.push(`/orders/${encodeURIComponent(orderNo)}`)}
        />
      </div>
    </div>
  );
}
