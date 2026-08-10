import React from 'react';
import { history, useParams } from 'umi';
import { RefundConfirmation } from '../../../features/refund-confirmation/RefundConfirmation';
import { AlternativeShowList } from '../../../features/alternative-show-list/AlternativeShowList';
import { useRefundPage } from '../../../modules/order/transaction-hooks';
import { buildAlternativeShowSeatPath } from '../../../modules/order/routes';
import { formatOrderDateTime } from '../../../modules/order/formatters';
import {
  ORDERS_BREADCRUMB_ITEM,
  PROFILE_BREADCRUMB_ITEM,
  TransactionBreadcrumb,
} from '../../../features/transaction-breadcrumb/TransactionBreadcrumb';
import './index.css';

/**
 * 退票与替代场次页面：/orders/:orderNo/refund
 * 退票响应未知后只查询原退款；替代场次跳转后重新读取权威座位图。
 */
export default function RefundPage() {
  const { orderNo = '' } = useParams<{ orderNo: string }>();
  const state = useRefundPage(orderNo);
  const refundStatus = state.refund?.refundStatus;
  const status = state.loading
    ? 'LOADING'
    : state.resultUnknown
      ? 'RESULT_UNKNOWN'
      : refundStatus === 'SUCCESS'
        ? 'SUCCESS'
        : refundStatus === 'REQUESTED'
          ? 'REQUESTED'
          : state.submitting || refundStatus === 'PROCESSING'
            ? 'PROCESSING'
            : state.error?.code === 205006
              ? 'NOT_REFUNDABLE'
              : state.error
                ? 'ERROR'
                : 'NORMAL';
  // 影响查询可能因订单状态变化返回 205006，但已有退款记录仍是服务端更具体的结果。
  const visibleError =
    state.error?.code === 205006 && state.refund ? undefined : state.error?.message;
  const impact = state.impact;
  const alternativeShows = (state.alternatives?.shows ?? []).map((show) => ({
    ...show,
    startTime: formatOrderDateTime(show.startTime),
  }));
  return (
    <div className="refund-page-wrapper">
      <div className="refund-page-content">
        <TransactionBreadcrumb
          items={[
            PROFILE_BREADCRUMB_ITEM,
            ORDERS_BREADCRUMB_ITEM,
            { label: '订单详情', to: `/orders/${encodeURIComponent(orderNo)}` },
            { label: '申请退票' },
          ]}
        />
        <RefundConfirmation
          orderNo={impact?.orderNo ?? orderNo}
          refundAmount={impact?.refundAmount ?? state.refund?.refundAmount ?? '0.00'}
          orderStatus={impact?.orderStatus ?? state.refund?.orderStatus ?? 'PAID'}
          ticketStatus={impact?.ticketStatus ?? state.refund?.ticketStatus ?? 'VALID'}
          showStartTime={formatOrderDateTime(impact?.showStartTime)}
          impactText={impact?.impactText ?? '正在读取服务端退票影响说明。'}
          status={status}
          error={visibleError}
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
