import type { PaymentResultStatus } from '../../../features/payment-result/PaymentResult';
import type { PaymentResponse } from '../../../modules/order/types';

/**
 * 将服务端支付/订单快照映射为支付结果页的展示状态。
 *
 * 订单终态必须保留各自原因；取消和退款不能被错误归类为超时过期。
 */
export function resolvePaymentResultStatus(
  payment: PaymentResponse | null,
  isResultUnknown: boolean,
  hasQueryError: boolean,
): PaymentResultStatus {
  // 写请求响应丢失时，不能把未知结果错误显示为可再次支付的待支付状态。
  if (isResultUnknown) {
    return 'RESULT_UNKNOWN';
  }
  if (hasQueryError) {
    return 'ERROR';
  }
  if (payment === null) {
    return 'CONFIRMING';
  }
  if (payment.paymentStatus === 'SUCCESS' && payment.orderStatus === 'PAID') {
    return 'SUCCESS';
  }
  switch (payment.orderStatus) {
    case 'PENDING_PAYMENT':
      return 'PENDING_PAYMENT';
    case 'CANCELLED':
      return 'CANCELLED';
    case 'EXPIRED':
      return 'EXPIRED';
    case 'REFUNDED':
      return 'REFUNDED';
    case 'PAYING':
    case 'PAID':
    case 'REFUNDING':
    default:
      return payment.paymentStatus === 'PROCESSING' ? 'PROCESSING' : 'CONFIRMING';
  }
}
