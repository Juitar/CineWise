import React, { useMemo } from 'react';
import { useParams } from 'umi';
import { ElectronicTicketCard } from '../../features/electronic-ticket-card/ElectronicTicketCard';
import { useElectronicTicket, useOrder } from '../../modules/order/transaction-hooks';
import { formatOrderDateTime } from '../../modules/order/formatters';
import {
  ORDERS_BREADCRUMB_ITEM,
  PROFILE_BREADCRUMB_ITEM,
  TransactionBreadcrumb,
} from '../../features/transaction-breadcrumb/TransactionBreadcrumb';
import { OrderContentNotice } from '../../features/order-content-notice/OrderContentNotice';
import { useOrderContentDetails } from '../../modules/order/useOrderContentDetails';
import './index.css';

/**
 * 电子票展示页面：/tickets/:ticketId
 * 只展示服务端权威票状态，不提供核销或状态写入能力。
 */
export default function TicketPage() {
  const { ticketId = '' } = useParams<{ ticketId: string }>();
  const ticketQuery = useElectronicTicket(ticketId);
  const ticket = ticketQuery.data;
  const orderQuery = useOrder(ticket?.orderNo ?? '');
  // 路由快速切换时旧订单响应不能补到新票据上，只接受 orderNo 完全一致的上下文。
  const order = orderQuery.data?.orderNo === ticket?.orderNo ? orderQuery.data : null;
  const content = useOrderContentDetails(
    useMemo(() => (order ? [{ movieId: order.movieId, cinemaId: order.cinemaId }] : []), [order]),
  );
  const movie = order ? content.moviesById.get(order.movieId) : undefined;
  const cinema = order ? content.cinemasById.get(order.cinemaId) : undefined;
  const loading = ticketQuery.loading || (ticket !== null && orderQuery.loading);
  const error = ticketQuery.error?.message ?? orderQuery.error?.message;

  return (
    <div className="ticket-page-wrapper">
      <div className="ticket-page-content">
        <TransactionBreadcrumb
          items={
            order?.orderNo
              ? [
                  PROFILE_BREADCRUMB_ITEM,
                  ORDERS_BREADCRUMB_ITEM,
                  { label: '订单详情', to: `/orders/${encodeURIComponent(order.orderNo)}` },
                  { label: '电子票' },
                ]
              : [PROFILE_BREADCRUMB_ITEM, ORDERS_BREADCRUMB_ITEM, { label: '电子票' }]
          }
        />
        <OrderContentNotice
          isLoading={content.isLoading}
          hasUnavailableContent={content.hasUnavailableContent}
          onRetry={content.refresh}
        />
        <ElectronicTicketCard
          ticketCode={ticket?.ticketCode ?? ''}
          orderNo={ticket?.orderNo ?? ''}
          showTitle={ticket ? (movie?.title ?? '影片信息暂不可用') : undefined}
          showId={order?.showId ?? ticket?.showId}
          showTime={formatOrderDateTime(order?.showStartTime)}
          posterUrl={movie?.posterUrl}
          cinemaName={cinema?.name ?? '影院信息暂不可用'}
          cinemaArea={cinema?.area ?? undefined}
          cinemaAddress={cinema?.address ?? undefined}
          seatLabels={ticket?.seatIds}
          issuedAt={ticket?.issuedAt ? formatOrderDateTime(ticket.issuedAt) : undefined}
          status={ticket?.status ?? 'INVALIDATED'}
          invalidationReason={ticket?.invalidationReason}
          loading={loading}
          error={error}
          qrPayload={ticket?.qrPayload}
        />
      </div>
    </div>
  );
}
