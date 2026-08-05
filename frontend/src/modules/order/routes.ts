import type { AlternativeShow } from './types';

type AlternativeShowRouteContext = Pick<AlternativeShow, 'showId' | 'movieId' | 'cinemaId'>;

/** 只使用服务端候选场次返回的权威 ID 构造选座路由。 */
export function buildAlternativeShowSeatPath(show: AlternativeShowRouteContext): string {
  const showId = encodeURIComponent(show.showId);
  const movieId = encodeURIComponent(show.movieId);
  const cinemaId = encodeURIComponent(show.cinemaId);
  return `/shows/${showId}/seats?movieId=${movieId}&cinemaId=${cinemaId}`;
}

/**
 * 使用服务端订单号进入订单详情。
 *
 * 订单号是支付、电子票等后续流程的唯一关联标识，前端不得自行拼接或推断。
 */
export function buildOrderDetailPath(orderNo: string): string {
  return `/orders/${encodeURIComponent(orderNo)}`;
}

/**
 * 使用建单成功响应中的订单号进入支付页；此函数只生成导航地址，不触发支付写请求。
 */
export function buildPaymentPath(orderNo: string): string {
  return `/payments/${encodeURIComponent(orderNo)}`;
}

/** 使用支付成功响应中的电子票 ID 进入电子票详情。 */
export function buildElectronicTicketPath(ticketId: string): string {
  return `/tickets/${encodeURIComponent(ticketId)}`;
}
