import type { OrderStatus } from '../../../features/order-list/OrderList';

export interface OrderDetailMockData {
  orderId: string;
  orderNo: string;
  showId: string;
  movieId: string;
  cinemaId: string;
  showStartTime: string;
  seatIds: string[];
  ticketCount: number;
  unitPrice: string;
  totalAmount: string;
  status: OrderStatus;
  expireTime: string;
  stateVersion: number;
  updatedAt: string;
  showTitle: string;
  showTime: string;
  cinemaName: string;
  seatLabels: string[];
}

export const orderDetailMockPaid: OrderDetailMockData = {
  orderId: '10001',
  orderNo: '202608050001',
  showId: '1001',
  movieId: '2001',
  cinemaId: '3001',
  showStartTime: '2026-08-10T14:30:00+08:00',
  seatIds: ['101', '102'],
  ticketCount: 2,
  unitPrice: '39.00',
  totalAmount: '78.00',
  status: 'PAID',
  expireTime: '2026-08-05T12:15:00+08:00',
  stateVersion: 1,
  updatedAt: '2026-08-05T12:01:00+08:00',
  showTitle: '流浪地球3',
  showTime: '2026-08-10T14:30:00+08:00',
  cinemaName: '妙语影城（大悦城店）',
  seatLabels: ['5排10座', '5排11座'],
};

export const orderDetailMockPending: OrderDetailMockData = {
  ...orderDetailMockPaid,
  status: 'PENDING_PAYMENT',
};

export const orderDetailMockCancelled: OrderDetailMockData = {
  ...orderDetailMockPaid,
  status: 'CANCELLED',
};

export const orderDetailMockRefunded: OrderDetailMockData = {
  ...orderDetailMockPaid,
  status: 'REFUNDED',
};
