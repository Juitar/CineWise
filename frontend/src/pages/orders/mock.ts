import type { OrderSummaryItem } from '../../features/order-list/OrderList';

export interface OrderListMockData {
  orders: OrderSummaryItem[];
}

export const orderListMockNormal: OrderListMockData = {
  orders: [
    {
      orderId: '10001',
      orderNo: '202608050001',
      showTitle: '流浪地球3',
      showTime: '2026-08-10T14:30:00+08:00',
      ticketCount: 2,
      totalAmount: '78.00',
      status: 'PAID',
      cinemaName: '妙语影城（大悦城店）',
    },
    {
      orderId: '10002',
      orderNo: '202608050002',
      showTitle: '封神第二部',
      showTime: '2026-08-11T19:00:00+08:00',
      ticketCount: 1,
      totalAmount: '45.00',
      status: 'PENDING_PAYMENT',
      cinemaName: '妙语影城（大悦城店）',
    },
    {
      orderId: '10003',
      orderNo: '202608050003',
      showTitle: '哪吒之魔童闹海',
      showTime: '2026-08-01T10:00:00+08:00',
      ticketCount: 2,
      totalAmount: '80.00',
      status: 'REFUNDED',
      cinemaName: '妙语影城（大悦城店）',
    },
  ],
};

export const orderListMockEmpty: OrderListMockData = {
  orders: [],
};
