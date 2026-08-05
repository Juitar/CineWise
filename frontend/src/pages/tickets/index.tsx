import React from 'react';
import { useParams } from 'umi';
import { ElectronicTicketCard } from '../../features/electronic-ticket-card/ElectronicTicketCard';
import { useElectronicTicket, useOrder } from '../../modules/order/transaction-hooks';
import { formatOrderDateTime } from '../../modules/order/formatters';
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
  const loading = ticketQuery.loading || (ticket !== null && orderQuery.loading);
  const error = ticketQuery.error?.message ?? orderQuery.error?.message;

  return (
    <div className="ticket-page-wrapper">
      <ElectronicTicketCard
        ticketCode={ticket?.ticketCode ?? ''}
        orderNo={ticket?.orderNo ?? ''}
        showTitle={ticket ? '影片信息暂不可用' : undefined}
        showId={order?.showId ?? ticket?.showId}
        showTime={formatOrderDateTime(order?.showStartTime)}
        seatLabels={ticket?.seatIds}
        issuedAt={ticket?.issuedAt ? formatOrderDateTime(ticket.issuedAt) : undefined}
        status={ticket?.status ?? 'INVALIDATED'}
        loading={loading}
        error={error}
        qrPayload={ticket?.qrPayload}
      />
    </div>
  );
}
