import React from 'react';
import { history, useParams } from 'umi';
import { PaymentResult } from '../../../features/payment-result/PaymentResult';
import { useOrder, usePaymentResult } from '../../../modules/order/transaction-hooks';
import { resolvePaymentResultStatus } from './payment-result-status';
import {
  buildElectronicTicketPath,
  buildOrderDetailPath,
  buildPaymentPath,
} from '../../../modules/order/routes';
import { TransactionBackButton } from '../../../features/transaction-back-button/TransactionBackButton';
import './index.css';

/**
 * 支付结果页面：/payments/:orderNo/result
 * 自动查询有次数与总时长上限，只读恢复不会触发第二次支付 POST。
 */
export default function PaymentResultPage() {
  const { orderNo = '' } = useParams<{ orderNo: string }>();
  const orderQuery = useOrder(orderNo);
  const paymentAction = usePaymentResult(orderNo);
  const status = resolvePaymentResultStatus(
    paymentAction.payment,
    paymentAction.resultUnknown,
    paymentAction.error !== null,
  );

  return (
    <div className="payment-result-page-wrapper">
      <div className="payment-result-page-content">
        <TransactionBackButton onBack={() => history.push('/orders')} label="返回订单列表" />
        <PaymentResult
          orderNo={orderNo}
          amount={orderQuery.data?.totalAmount ?? '0.00'}
          status={status}
          error={paymentAction.error?.message}
          ticketId={paymentAction.payment?.ticketId}
          onViewTicket={() => {
            const ticketId = paymentAction.payment?.ticketId;
            if (status === 'SUCCESS' && ticketId) {
              history.push(buildElectronicTicketPath(ticketId));
            }
          }}
          onViewOrder={() => history.push(buildOrderDetailPath(orderNo))}
          onRetryQuery={() => void paymentAction.query()}
          onRetryPay={() => history.push(buildPaymentPath(orderNo))}
          onBackToHome={() => history.push('/')}
        />
      </div>
    </div>
  );
}
