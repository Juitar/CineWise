import { describe, expect, it } from 'vitest';
import { buildAlternativeShowSeatPath } from './routes';

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
});
