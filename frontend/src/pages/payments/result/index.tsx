import React from 'react';
import { history, useParams } from 'umi';
import { PaymentResult } from '../../../features/payment-result/PaymentResult';
import { useOrder, usePaymentResult } from '../../../modules/order/transaction-hooks';
import { resolvePaymentResultStatus } from './payment-result-status';
import './index.css';

/**
 * 支付结果页面：/payments/:orderNo/result
 * 自动查询有次数与总时长上限，只读恢复不会触发第二次支付 POST。
 */
export default function PaymentResultPage() {
  const { orderNo = '' } = useParams<{ orderNo: string }>();
  const orderQuery = useOrder(orderNo);
  const paymentAction = usePaymentResult(orderNo);
  const status = resolvePaymentResultStatus(paymentAction.payment, paymentAction.error !== null);

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
