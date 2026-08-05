import {
  AdminOrderDetailView,
  AdminOrderPageView,
  AdminOrderSummaryView,
} from '../../../features/admin-order/types';

// Mock: 待支付订单
export const mockSummaryPending: AdminOrderSummaryView = {
  orderId: '10001',
  orderNo: 'CW-PENDING-1001',
  userId: '1001',
  emailMasked: 'a***@example.com',
  showId: '2001',
  movieId: '501',
  cinemaId: '601',
  showStartTime: '2026-08-10T14:30:00+08:00',
  ticketCount: 2,
  unitPrice: '45.00',
  totalAmount: '90.00',
  orderStatus: 'PENDING_PAYMENT',
  expireTime: '2026-08-05T10:45:00+08:00',
  paymentStatus: null,
  ticketStatus: null,
  refundStatus: null,
  stateVersion: 1,
  createdAt: '2026-08-05T10:30:00+08:00',
  updatedAt: '2026-08-05T10:30:00+08:00',
};

// Mock: 已支付出票订单
export const mockSummaryPaid: AdminOrderSummaryView = {
  orderId: '10002',
  orderNo: 'CW-PAID-1002',
  userId: '1002',
  emailMasked: 'b***@example.com',
  showId: '2002',
  movieId: '502',
  cinemaId: '602',
  showStartTime: '2026-08-11T19:00:00+08:00',
  ticketCount: 1,
  unitPrice: '68.00',
  totalAmount: '68.00',
  orderStatus: 'PAID',
  expireTime: '2026-08-04T08:15:00+08:00',
  paymentStatus: 'SUCCESS',
  ticketStatus: 'VALID',
  refundStatus: null,
  stateVersion: 2,
  createdAt: '2026-08-04T08:00:00+08:00',
  updatedAt: '2026-08-04T08:05:00+08:00',
};

// Mock: 已退款订单
export const mockSummaryRefunded: AdminOrderSummaryView = {
  orderId: '3002',
  orderNo: 'CW-REFUNDED-1',
  userId: '1002',
  emailMasked: 'r***@example.com',
  showId: '2002',
  movieId: '502',
  cinemaId: '602',
  showStartTime: '2026-08-06T09:00:00+08:00',
  ticketCount: 1,
  unitPrice: '68.00',
  totalAmount: '68.00',
  orderStatus: 'REFUNDED',
  expireTime: '2026-08-04T08:15:00+08:00',
  paymentStatus: 'SUCCESS',
  ticketStatus: 'REFUNDED',
  refundStatus: 'SUCCESS',
  stateVersion: 3,
  createdAt: '2026-08-04T08:00:00+08:00',
  updatedAt: '2026-08-04T09:00:00+08:00',
};

// Mock: 历史用户订单 (emailMasked=null)
export const mockSummaryNoUser: AdminOrderSummaryView = {
  orderId: '10004',
  orderNo: 'CW-NOUSER-1004',
  userId: '1004',
  emailMasked: null,
  showId: '2004',
  movieId: '504',
  cinemaId: '604',
  showStartTime: '2026-08-15T15:00:00+08:00',
  ticketCount: 3,
  unitPrice: '50.00',
  totalAmount: '150.00',
  orderStatus: 'PAID',
  expireTime: '2026-08-01T08:15:00+08:00',
  paymentStatus: 'SUCCESS',
  ticketStatus: 'VALID',
  refundStatus: null,
  stateVersion: 2,
  createdAt: '2026-08-01T08:00:00+08:00',
  updatedAt: '2026-08-01T08:05:00+08:00',
};

export const mockPageNormal: AdminOrderPageView<AdminOrderSummaryView> = {
  total: 4,
  page: 1,
  size: 20,
  records: [mockSummaryPending, mockSummaryPaid, mockSummaryRefunded, mockSummaryNoUser],
};

export const mockPageEmpty: AdminOrderPageView<AdminOrderSummaryView> = {
  total: 0,
  page: 1,
  size: 20,
  records: [],
};

export const mockDetailPending: AdminOrderDetailView = {
  summary: mockSummaryPending,
  paidTime: null,
  cancelledTime: null,
  refundedTime: null,
  seats: [
    { seatId: '4001', rowNo: 'A', seatNo: '01', unitPrice: '45.00' },
    { seatId: '4002', rowNo: 'A', seatNo: '02', unitPrice: '45.00' },
  ],
  payment: null,
  ticket: null,
  refund: null,
};

export const mockDetailPaid: AdminOrderDetailView = {
  summary: mockSummaryPaid,
  paidTime: '2026-08-04T08:05:00+08:00',
  cancelledTime: null,
  refundedTime: null,
  seats: [{ seatId: '4003', rowNo: 'C', seatNo: '12', unitPrice: '68.00' }],
  payment: {
    paymentNo: 'PAY-PAID-1002',
    amount: '68.00',
    status: 'SUCCESS',
    requestedAt: '2026-08-04T08:01:00+08:00',
    paidAt: '2026-08-04T08:05:00+08:00',
    stateVersion: 1,
    updatedAt: '2026-08-04T08:05:00+08:00',
  },
  ticket: {
    ticketCode: 'TICKET-PAID-1002',
    status: 'VALID',
    issuedAt: '2026-08-04T08:05:00+08:00',
    invalidatedAt: null,
    stateVersion: 1,
    updatedAt: '2026-08-04T08:05:00+08:00',
  },
  refund: null,
};

export const mockDetailRefunded: AdminOrderDetailView = {
  summary: mockSummaryRefunded,
  paidTime: '2026-08-04T08:05:00+08:00',
  cancelledTime: null,
  refundedTime: '2026-08-04T08:50:00+08:00',
  seats: [{ seatId: '4004', rowNo: 'B', seatNo: '08', unitPrice: '68.00' }],
  payment: {
    paymentNo: 'PAY-REFUNDED-1',
    amount: '68.00',
    status: 'SUCCESS',
    requestedAt: '2026-08-04T08:10:00+08:00',
    paidAt: '2026-08-04T08:20:00+08:00',
    stateVersion: 1,
    updatedAt: '2026-08-04T08:20:00+08:00',
  },
  ticket: {
    ticketCode: 'TICKET-REFUNDED-1',
    status: 'REFUNDED',
    issuedAt: '2026-08-04T08:20:00+08:00',
    invalidatedAt: '2026-08-04T08:50:00+08:00',
    stateVersion: 1,
    updatedAt: '2026-08-04T08:50:00+08:00',
  },
  refund: {
    refundNo: 'REFUND-1',
    reason: '行程变化',
    status: 'SUCCESS',
    requestedAt: '2026-08-04T08:40:00+08:00',
    processedAt: '2026-08-04T08:50:00+08:00',
    stateVersion: 2,
    updatedAt: '2026-08-04T08:50:00+08:00',
  },
};

export const mockDetailNoUser: AdminOrderDetailView = {
  summary: mockSummaryNoUser,
  paidTime: '2026-08-01T08:05:00+08:00',
  cancelledTime: null,
  refundedTime: null,
  seats: [
    { seatId: '4005', rowNo: 'D', seatNo: '01', unitPrice: '50.00' },
    { seatId: '4006', rowNo: 'D', seatNo: '02', unitPrice: '50.00' },
    { seatId: '4007', rowNo: 'D', seatNo: '03', unitPrice: '50.00' },
  ],
  payment: {
    paymentNo: 'PAY-NOUSER-1004',
    amount: '150.00',
    status: 'SUCCESS',
    requestedAt: '2026-08-01T08:01:00+08:00',
    paidAt: '2026-08-01T08:05:00+08:00',
    stateVersion: 1,
    updatedAt: '2026-08-01T08:05:00+08:00',
  },
  ticket: {
    ticketCode: 'TICKET-NOUSER-1004',
    status: 'VALID',
    issuedAt: '2026-08-01T08:05:00+08:00',
    invalidatedAt: null,
    stateVersion: 1,
    updatedAt: '2026-08-01T08:05:00+08:00',
  },
  refund: null,
};

export const mockDetailMap: Record<string, AdminOrderDetailView> = {
  'CW-PENDING-1001': mockDetailPending,
  'CW-PAID-1002': mockDetailPaid,
  'CW-REFUNDED-1': mockDetailRefunded,
  'CW-NOUSER-1004': mockDetailNoUser,
};
