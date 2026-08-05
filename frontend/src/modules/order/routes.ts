import type { AlternativeShow } from './types';

type AlternativeShowRouteContext = Pick<AlternativeShow, 'showId' | 'movieId' | 'cinemaId'>;

/** 只使用服务端候选场次返回的权威 ID 构造选座路由。 */
export function buildAlternativeShowSeatPath(show: AlternativeShowRouteContext): string {
  const showId = encodeURIComponent(show.showId);
  const movieId = encodeURIComponent(show.movieId);
  const cinemaId = encodeURIComponent(show.cinemaId);
  return `/shows/${showId}/seats?movieId=${movieId}&cinemaId=${cinemaId}`;
}
