import React from 'react';
import { useParams } from 'umi';
import { ElectronicTicketCard } from '../../features/electronic-ticket-card/ElectronicTicketCard';
import { useElectronicTicket } from '../../modules/order/transaction-hooks';
import './index.css';

/**
 * 电子票展示页面：/tickets/:ticketId
 * 只展示服务端权威票状态，不提供核销或状态写入能力。
 */
export default function TicketPage() {
  const { ticketId = '' } = useParams<{ ticketId: string }>();
  const ticketQuery = useElectronicTicket(ticketId);
  const ticket = ticketQuery.data;

  return (
    <div className="ticket-page-wrapper">
      <ElectronicTicketCard
        ticketCode={ticket?.ticketCode ?? ''}
        orderNo={ticket?.orderNo ?? ''}
        showTitle={ticket ? `场次 ${ticket.showId}` : undefined}
        showTime="场次时间请在订单中确认"
        seatLabels={ticket?.seatIds}
        issuedAt={ticket?.issuedAt}
        status={ticket?.status ?? 'INVALIDATED'}
        loading={ticketQuery.loading}
        error={ticketQuery.error?.message}
        qrPayload={ticket?.qrPayload}
      />
    </div>
  );
}
