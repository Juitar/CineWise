import { apiRequest } from '../../shared/api/client';
import type { ShowSummary, SeatMapResponse } from './types';

/**
 * 查询固定影片和影院的未来可售场次列表
 * @param movieId 十进制字符串影片ID
 * @param cinemaId 十进制字符串影院ID
 */
export async function getShows(movieId: string, cinemaId: string): Promise<ShowSummary[]> {
  const queryParams = new URLSearchParams();
  if (movieId) {
    queryParams.append('movieId', movieId);
  }
  if (cinemaId) {
    queryParams.append('cinemaId', cinemaId);
  }
  const queryString = queryParams.toString();
  const url = `/api/v1/shows${queryString ? `?${queryString}` : ''}`;
  return apiRequest<ShowSummary[]>(url, { method: 'GET' });
}

/**
 * 登录后查询场次权威座位图
 * @param showId 十进制字符串场次ID
 */
export async function getSeatMap(showId: string): Promise<SeatMapResponse> {
  return apiRequest<SeatMapResponse>(`/api/v1/shows/${encodeURIComponent(showId)}/seats`, {
    method: 'GET',
  });
}
