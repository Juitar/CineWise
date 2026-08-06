import React from 'react';
import { history, useParams } from 'umi';
import { RefundConfirmation } from '../../../features/refund-confirmation/RefundConfirmation';
import { AlternativeShowList } from '../../../features/alternative-show-list/AlternativeShowList';
import { useRefundPage } from '../../../modules/order/transaction-hooks';
import { buildAlternativeShowSeatPath } from '../../../modules/order/routes';
import { formatOrderDateTime } from '../../../modules/order/formatters';
import { TransactionBackButton } from '../../../features/transaction-back-button/TransactionBackButton';
import './index.css';

/**
 * 退票与替代场次页面：/orders/:orderNo/refund
 * 退票响应未知后只查询原退款；替代场次跳转后重新读取权威座位图。
 */
export default function RefundPage() {
  const { orderNo = '' } = useParams<{ orderNo: string }>();
  const state = useRefundPage(orderNo);
  const status = state.loading
    ? 'LOADING'
    : state.resultUnknown
      ? 'RESULT_UNKNOWN'
      : state.error?.code === 205006
        ? 'NOT_REFUNDABLE'
        : state.refund?.refundStatus === 'SUCCESS'
          ? 'SUCCESS'
          : state.refund?.refundStatus === 'REQUESTED'
            ? 'REQUESTED'
            : state.submitting || state.refund?.refundStatus === 'PROCESSING'
              ? 'PROCESSING'
              : state.error
                ? 'ERROR'
                : 'NORMAL';
  const impact = state.impact;
  const alternativeShows = (state.alternatives?.shows ?? []).map((show) => ({
    ...show,
    startTime: formatOrderDateTime(show.startTime),
  }));
  return (
    <div className="refund-page-wrapper">
      <div className="refund-page-content">
        <TransactionBackButton
          onBack={() => history.push(`/orders/${encodeURIComponent(orderNo)}`)}
          label="返回订单详情"
        />
        <RefundConfirmation
          orderNo={impact?.orderNo ?? orderNo}
          refundAmount={impact?.refundAmount ?? state.refund?.refundAmount ?? '0.00'}
          orderStatus={impact?.orderStatus ?? state.refund?.orderStatus ?? 'PAID'}
          ticketStatus={impact?.ticketStatus ?? state.refund?.ticketStatus ?? 'VALID'}
          showStartTime={formatOrderDateTime(impact?.showStartTime)}
          impactText={impact?.impactText ?? '正在读取服务端退票影响说明。'}
          status={status}
          error={state.error?.message}
          onConfirmRefund={(reason) => void state.submit(reason)}
          onQueryRefundResult={() => void state.recover()}
          onCancel={() => history.push(`/orders/${encodeURIComponent(orderNo)}`)}
        />

        <AlternativeShowList
          shows={alternativeShows}
          loading={state.loading}
          error={state.alternativeError?.message}
          onSelectShow={(show) => history.push(buildAlternativeShowSeatPath(show))}
        />
      </div>
    </div>
  );
}
