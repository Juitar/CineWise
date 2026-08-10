import React, { useMemo } from 'react';
import { useLocation, useParams } from 'umi';
import { ElectronicTicketCard } from '../../features/electronic-ticket-card/ElectronicTicketCard';
import { useElectronicTicket, useOrder } from '../../modules/order/transaction-hooks';
import { formatOrderDateTime } from '../../modules/order/formatters';
import {
  PROFILE_BREADCRUMB_ITEM,
  TransactionBreadcrumb,
} from '../../features/transaction-breadcrumb/TransactionBreadcrumb';
import { OrderContentNotice } from '../../features/order-content-notice/OrderContentNotice';
import { useOrderContentDetails } from '../../modules/order/useOrderContentDetails';
import { useSeatMap } from '../../modules/ticketing/hooks';
import { workspacePath } from '../../modules/agent/workspaceRoute';
import './index.css';

/**
 * 电子票展示页面：/tickets/:ticketId
 * 只展示服务端权威票状态，不提供核销或状态写入能力。
 */
export default function TicketPage() {
  const { ticketId = '' } = useParams<{ ticketId: string }>();
  const location = useLocation();
  const ticketQuery = useElectronicTicket(ticketId);
  const ticket = ticketQuery.data;
  const orderQuery = useOrder(ticket?.orderNo ?? '');
  // 路由快速切换时旧订单响应不能补到新票据上，只接受 orderNo 完全一致的上下文。
  const order = orderQuery.data?.orderNo === ticket?.orderNo ? orderQuery.data : null;
  const seatMap = useSeatMap(order?.showId);
  const content = useOrderContentDetails(
    useMemo(() => (order ? [{ movieId: order.movieId, cinemaId: order.cinemaId }] : []), [order]),
  );
  const movie = order ? content.moviesById.get(order.movieId) : undefined;
  const cinema = order ? content.cinemasById.get(order.cinemaId) : undefined;
  const seatLabels = useMemo(() => {
    if (!ticket || !seatMap.seatMap) {
      return [];
    }
    const labelsById = new Map(seatMap.seatMap.seats.map((seat) => [seat.seatId, seat.seatLabel]));
    // 票据接口只返回内部 seatId；票面必须用场次座位图映射后的行列号展示。
    return ticket.seatIds.flatMap((seatId) => {
      const label = labelsById.get(seatId);
      return label ? [label] : [];
    });
  }, [seatMap.seatMap, ticket]);
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
                  { label: '我的订单', to: workspacePath(location.pathname, '/orders') },
                  {
                    label: '订单详情',
                    to: workspacePath(
                      location.pathname,
                      `/orders/${encodeURIComponent(order.orderNo)}`,
                    ),
                  },
                  { label: '电子票' },
                ]
              : [
                  PROFILE_BREADCRUMB_ITEM,
                  { label: '我的订单', to: workspacePath(location.pathname, '/orders') },
                  { label: '电子票' },
                ]
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
          showTime={formatOrderDateTime(order?.showStartTime)}
          posterUrl={movie?.posterUrl}
          cinemaName={cinema?.name ?? '影院信息暂不可用'}
          cinemaArea={cinema?.area ?? undefined}
          cinemaAddress={cinema?.address ?? undefined}
          seatLabels={seatLabels}
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
