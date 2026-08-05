import React from 'react';
import { history, useParams } from 'umi';
import { PaymentResult } from '../../../features/payment-result/PaymentResult';
import type { PaymentResultStatus } from '../../../features/payment-result/PaymentResult';
import { useOrder, usePaymentResult } from '../../../modules/order/transaction-hooks';
import './index.css';

/**
 * 支付结果页面：/payments/:orderNo/result
 * 自动查询有次数与总时长上限，只读恢复不会触发第二次支付 POST。
 */
export default function PaymentResultPage() {
  const { orderNo = '' } = useParams<{ orderNo: string }>();
  const orderQuery = useOrder(orderNo);
  const paymentAction = usePaymentResult(orderNo);
  const payment = paymentAction.payment;
  let status: PaymentResultStatus = paymentAction.error ? 'ERROR' : 'CONFIRMING';
  if (payment?.paymentStatus === 'SUCCESS' && payment.orderStatus === 'PAID') {
    status = 'SUCCESS';
  } else if (payment?.orderStatus === 'PENDING_PAYMENT') {
    status = 'PENDING_PAYMENT';
  } else if (
    payment?.orderStatus === 'CANCELLED' ||
    payment?.orderStatus === 'EXPIRED' ||
    payment?.orderStatus === 'REFUNDED'
  ) {
    status = 'EXPIRED';
  } else if (payment?.paymentStatus === 'PROCESSING') {
    status = 'PROCESSING';
  }

  return (
    <div className="payment-result-page-wrapper">
      <PaymentResult
        orderNo={orderNo}
        amount={orderQuery.data?.totalAmount ?? '0.00'}
        status={status}
        error={paymentAction.error?.message}
        onViewOrder={() => history.push(`/orders/${encodeURIComponent(orderNo)}`)}
        onRetryQuery={() => void paymentAction.query()}
        onRetryPay={() => history.push(`/payments/${encodeURIComponent(orderNo)}`)}
        onBackToHome={() => history.push('/')}
      />
    </div>
  );
}
