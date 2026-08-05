import type { PaymentResultStatus } from '../../../features/payment-result/PaymentResult';

export interface PaymentResultMockData {
  orderNo: string;
  amount: string;
  status: PaymentResultStatus;
}

export const paymentResultMockSuccess: PaymentResultMockData = {
  orderNo: '202608050001',
  amount: '78.00',
  status: 'SUCCESS',
};

export const paymentResultMockProcessing: PaymentResultMockData = {
  orderNo: '202608050001',
  amount: '78.00',
  status: 'PROCESSING',
};

export const paymentResultMockUnknown: PaymentResultMockData = {
  orderNo: '202608050001',
  amount: '78.00',
  status: 'RESULT_UNKNOWN',
};

export const paymentResultMockPending: PaymentResultMockData = {
  orderNo: '202608050001',
  amount: '78.00',
  status: 'PENDING_PAYMENT',
};

export const paymentResultMockExpired: PaymentResultMockData = {
  orderNo: '202608050001',
  amount: '78.00',
  status: 'EXPIRED',
};
