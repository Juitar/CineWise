import React from 'react';
import { history, useLocation, useParams } from 'umi';
import { PaymentResult } from '../../../features/payment-result/PaymentResult';
import { useOrder, usePaymentResult } from '../../../modules/order/transaction-hooks';
import { resolvePaymentResultStatus } from './payment-result-status';
import {
  buildElectronicTicketPath,
  buildOrderDetailPath,
  buildPaymentPath,
} from '../../../modules/order/routes';
import {
  PROFILE_BREADCRUMB_ITEM,
  TransactionBreadcrumb,
} from '../../../features/transaction-breadcrumb/TransactionBreadcrumb';
import { workspacePath } from '../../../modules/agent/workspaceRoute';
import './index.css';

/**
 * 支付结果页面：/payments/:orderNo/result
 * 自动查询有次数与总时长上限，只读恢复不会触发第二次支付 POST。
 */
export default function PaymentResultPage() {
  const { orderNo = '' } = useParams<{ orderNo: string }>();
  const location = useLocation();
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
        <TransactionBreadcrumb
          items={[
            PROFILE_BREADCRUMB_ITEM,
            { label: '我的订单', to: workspacePath(location.pathname, '/orders') },
            {
              label: '订单详情',
              to: workspacePath(location.pathname, buildOrderDetailPath(orderNo)),
            },
            { label: '支付结果' },
          ]}
        />
        <PaymentResult
          orderNo={orderNo}
          amount={orderQuery.data?.totalAmount ?? '0.00'}
          status={status}
          error={paymentAction.error?.message}
          ticketId={paymentAction.payment?.ticketId}
          onViewTicket={() => {
            const ticketId = paymentAction.payment?.ticketId;
            if (status === 'SUCCESS' && ticketId) {
              history.push(workspacePath(location.pathname, buildElectronicTicketPath(ticketId)));
            }
          }}
          onViewOrder={() =>
            history.push(workspacePath(location.pathname, buildOrderDetailPath(orderNo)))
          }
          onRetryQuery={() => void paymentAction.query()}
          onRetryPay={() =>
            history.push(workspacePath(location.pathname, buildPaymentPath(orderNo)))
          }
        />
      </div>
    </div>
  );
}
