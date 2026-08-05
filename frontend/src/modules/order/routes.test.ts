import { describe, expect, it } from 'vitest';
import {
  buildAlternativeShowSeatPath,
  buildElectronicTicketPath,
  buildOrderDetailPath,
  buildPaymentPath,
} from './routes';

describe('订单场次路由', () => {
  it('只使用候选响应中的三个 ID 并逐项编码', () => {
    expect(
      buildAlternativeShowSeatPath({
        showId: 'show/1',
        movieId: 'movie 2',
        cinemaId: 'cinema&3',
      }),
    ).toBe('/shows/show%2F1/seats?movieId=movie%202&cinemaId=cinema%263');
  });

  it('使用服务端返回的订单号和电子票 ID 构造并编码交易出口', () => {
    expect(buildOrderDetailPath('CW/100 01')).toBe('/orders/CW%2F100%2001');
    expect(buildPaymentPath('CW/100 01')).toBe('/payments/CW%2F100%2001');
    expect(buildElectronicTicketPath('ticket/100 01')).toBe('/tickets/ticket%2F100%2001');
  });
});
