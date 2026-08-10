import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { CinemaDetail } from '../../shared/types/api';
import { useCinemaDetail } from './useCinemaDetail';

const mocks = vi.hoisted(() => ({ queryCinemaDetail: vi.fn() }));

vi.mock('./api', () => ({ queryCinemaDetail: mocks.queryCinemaDetail }));

const detail = (cinemaId: string, name: string): CinemaDetail => ({
  cinemaId,
  name,
  cityCode: '430100',
  area: '岳麓区',
  address: '梅溪湖路1号',
  longitude: null,
  latitude: null,
  source: 'NETSTART_MAOYAN',
  sourceType: 'LIVE',
  dataTime: '2026-08-05T10:00:00+08:00',
  expiresAt: '2026-08-05T16:00:00+08:00',
  isExpired: false,
  degraded: false,
  fallbackType: null,
});

describe('useCinemaDetail', () => {
  beforeEach(() => mocks.queryCinemaDetail.mockReset());

  it('影院切换时取消旧请求且只保留当前响应', async () => {
    let resolveFirst: ((value: CinemaDetail) => void) | undefined;
    mocks.queryCinemaDetail
      .mockImplementationOnce(
        () =>
          new Promise<CinemaDetail>((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockResolvedValueOnce(detail('2', '第二家影院'));
    const { result, rerender } = renderHook(({ cinemaId }) => useCinemaDetail(cinemaId), {
      initialProps: { cinemaId: '1' },
    });
    const firstSignal = mocks.queryCinemaDetail.mock.calls[0][1] as AbortSignal;

    rerender({ cinemaId: '2' });
    expect(firstSignal.aborted).toBe(true);
    await waitFor(() => expect(result.current.data?.cinemaId).toBe('2'));
    await act(async () => {
      resolveFirst?.(detail('1', '第一家影院'));
      await Promise.resolve();
    });
    expect(result.current.data?.name).toBe('第二家影院');
  });
});
