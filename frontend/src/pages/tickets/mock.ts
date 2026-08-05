import type { ElectronicTicketStatus } from '../../features/electronic-ticket-card/ElectronicTicketCard';

export interface ElectronicTicketMockData {
  ticketId: string;
  ticketCode: string;
  orderId: string;
  orderNo: string;
  showId: string;
  movieId: string;
  cinemaId: string;
  showStartTime: string;
  seatIds: string[];
  status: ElectronicTicketStatus;
  qrPayload: string;
  issuedAt: string;
  stateVersion: number;
  updatedAt: string;
  showTitle: string;
  showTime: string;
  cinemaName: string;
  seatLabels: string[];
}

export const ticketMockValid: ElectronicTicketMockData = {
  ticketId: '10001',
  ticketCode: '202608050001',
  orderId: '10001',
  orderNo: '202608050001',
  showId: '1001',
  movieId: '2001',
  cinemaId: '3001',
  showStartTime: '2026-08-10T14:30:00+08:00',
  seatIds: ['101', '102'],
  status: 'VALID',
  qrPayload: 'mock-qr-payload-ticket-10001',
  issuedAt: '2026-08-05T12:01:00+08:00',
  stateVersion: 1,
  updatedAt: '2026-08-05T12:01:00+08:00',
  showTitle: '流浪地球3',
  showTime: '2026-08-10T14:30:00+08:00',
  cinemaName: '妙语影城（大悦城店）',
  seatLabels: ['5排10座', '5排11座'],
};

export const ticketMockRefunded: ElectronicTicketMockData = {
  ...ticketMockValid,
  status: 'REFUNDED',
};

export const ticketMockInvalidated: ElectronicTicketMockData = {
  ...ticketMockValid,
  status: 'INVALIDATED',
};
