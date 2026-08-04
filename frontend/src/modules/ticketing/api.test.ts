import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getShows, getSeatMap } from './api';
import { ApiError } from '../../shared/api/ApiError';
import showListSuccessPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/show-list-success.json';
import seatMapSuccessPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/seat-map-success.json';
import seatConflictErrorPayload from '../../../../backend/src/test/resources/fixtures/ticketing/c/seat-conflict-error.json';

function createApiResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('票务场次与选座 API 测试 (基于 backend ticketing/c 夹具)', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('getShows 能正确解析场次成功夹具字段且金额和ID皆为 string', async () => {
    fetchMock.mockResolvedValueOnce(createApiResponse(showListSuccessPayload));

    const shows = await getShows('2084194398004512769', '2084194399128586242');
    expect(shows).toHaveLength(1);
    expect(shows[0].showId).toBe('2084194401305432066');
    expect(shows[0].basePrice).toBe('39.00');
    expect(typeof shows[0].showId).toBe('string');
    expect(typeof shows[0].basePrice).toBe('string');
  });

  it('getShows 收到空场次数据时返回空数组', async () => {
    fetchMock.mockResolvedValueOnce(
      createApiResponse({
        code: 0,
        message: 'success',
        data: [],
        traceId: 'test-trace',
      }),
    );

    const shows = await getShows('movieId', 'cinemaId');
    expect(shows).toEqual([]);
  });

  it('getSeatMap 能正确解析权威座位快照且座位 ID 为 string', async () => {
    fetchMock.mockResolvedValueOnce(createApiResponse(seatMapSuccessPayload));

    const seatMap = await getSeatMap('2084194401305432066');
    expect(seatMap.showId).toBe('2084194401305432066');
    expect(seatMap.seats).toHaveLength(2);
    expect(seatMap.seats[0].seatId).toBe('2084194402305432067');
    expect(seatMap.seats[0].status).toBe('AVAILABLE');
    expect(seatMap.seats[1].status).toBe('LOCKED');
  });

  it('遇到业务冲突错误码 (204001) 时能正确转为 ApiError 抛出', async () => {
    fetchMock.mockResolvedValueOnce(createApiResponse(seatConflictErrorPayload, 409));

    await expect(getSeatMap('showId')).rejects.toThrowError(ApiError);
  });
});
