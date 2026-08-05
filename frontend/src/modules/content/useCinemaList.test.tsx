import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import type { CinemaSummary, ContentPageResponse } from '../../shared/types/api';
import { useCinemaList } from './useCinemaList';

const contentMocks = vi.hoisted(() => ({ queryCinemas: vi.fn() }));

vi.mock('./api', () => ({ queryCinemas: contentMocks.queryCinemas }));

const response: ContentPageResponse<CinemaSummary> = {
  records: [
    {
      cinemaId: '8200001',
      name: '妙语影城·滨江店',
      cityCode: '430100',
      area: '滨江区',
      address: '江南大道88号',
    },
  ],
  total: 1,
  page: 1,
  size: 20,
  source: 'DEMO_CONTENT',
  sourceType: 'MOCK',
  dataTime: '2026-08-04T09:00:00+08:00',
  expiresAt: '2026-08-04T15:00:00+08:00',
  isExpired: false,
  degraded: true,
  fallbackType: 'MOCK',
};

const defaultQuery = { location: '430100', keyword: undefined, page: 1, size: 20 };

describe('useCinemaList', () => {
  beforeEach(() => {
    contentMocks.queryCinemas.mockReset();
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true });
  });

  it('加载影院响应并转为成功状态', async () => {
    contentMocks.queryCinemas.mockResolvedValue(response);
    const { result } = renderHook(() => useCinemaList(defaultQuery));

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.data).toBe(response));
    expect(result.current.error).toBeNull();
  });

  it('条件变化时取消旧请求且丢弃迟到响应', async () => {
    let resolveFirst: ((value: ContentPageResponse<CinemaSummary>) => void) | undefined;
    const secondResponse = {
      ...response,
      records: [{ ...response.records[0], name: '第二家影院' }],
    };
    contentMocks.queryCinemas
      .mockImplementationOnce(
        () =>
          new Promise<ContentPageResponse<CinemaSummary>>((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockResolvedValueOnce(secondResponse);
    const { result, rerender } = renderHook(
      ({ keyword }) => useCinemaList({ ...defaultQuery, keyword }),
      { initialProps: { keyword: '滨江' as string | undefined } },
    );
    const firstSignal = contentMocks.queryCinemas.mock.calls[0][1] as AbortSignal;

    rerender({ keyword: '西湖' });
    expect(firstSignal.aborted).toBe(true);
    await waitFor(() => expect(result.current.data).toBe(secondResponse));

    await act(async () => {
      resolveFirst?.(response);
      await Promise.resolve();
    });
    expect(result.current.data).toBe(secondResponse);
  });

  it('失败后保留旧数据并允许重试', async () => {
    contentMocks.queryCinemas
      .mockResolvedValueOnce(response)
      .mockRejectedValueOnce(new ApiError('offline', { kind: 'NETWORK' }))
      .mockResolvedValueOnce(response);
    const { result } = renderHook(() => useCinemaList(defaultQuery));
    await waitFor(() => expect(result.current.data).toBe(response));

    act(() => result.current.retry());
    await waitFor(() => expect(result.current.error?.kind).toBe('NETWORK'));
    expect(result.current.data).toBe(response);

    act(() => result.current.retry());
    await waitFor(() => expect(result.current.error).toBeNull());
    expect(contentMocks.queryCinemas).toHaveBeenCalledTimes(3);
  });
});
