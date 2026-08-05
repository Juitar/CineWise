export interface PaymentMockData {
  orderId: string;
  orderNo: string;
  paymentNo: string;
  orderStatus: string;
  paymentStatus: 'INITIALIZED' | 'PROCESSING' | 'SUCCESS';
  ticketId: string | null;
  stateVersion: number;
  updatedAt: string;
  ticketCount: number;
  totalAmount: string;
  showStartTime: string;
  countdownText: string;
}

/**
 * 模拟支付页集中 Mock 数据
 * 包含 Normal、Loading、Processing、Result unknown、Success、Error、Offline read-only 状态的演示样本
 * 严格遵守已冻结后端契约：paymentStatus 只能为 INITIALIZED、PROCESSING、SUCCESS；
 * ticketId 为 string | null；所有业务 ID 均使用十进制字符串示例；时间为 ISO 8601 带时区
 */
export const paymentMockNormal: PaymentMockData = {
  orderId: '10001',
  orderNo: '202608050001',
  paymentNo: '202608050001',
  orderStatus: 'PENDING_PAYMENT',
  paymentStatus: 'INITIALIZED',
  ticketId: null,
  stateVersion: 1,
  updatedAt: '2026-08-05T12:00:00+08:00',
  ticketCount: 2,
  totalAmount: '78.00',
  showStartTime: '2026-08-10T14:30:00+08:00',
  countdownText: '14分59秒',
};

export const paymentMockProcessing: PaymentMockData = {
  ...paymentMockNormal,
  paymentStatus: 'PROCESSING',
};

export const paymentMockUnknown: PaymentMockData = {
  ...paymentMockNormal,
  paymentStatus: 'PROCESSING',
};

export const paymentMockSuccess: PaymentMockData = {
  ...paymentMockNormal,
  orderStatus: 'PAID',
  paymentStatus: 'SUCCESS',
  ticketId: '10001',
};

export const paymentMockStates = {
  normal: paymentMockNormal,
  processing: paymentMockProcessing,
  unknown: paymentMockUnknown,
  success: paymentMockSuccess,
};
