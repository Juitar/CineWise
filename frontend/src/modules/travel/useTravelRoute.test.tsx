import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../shared/api/ApiError';

const api = vi.hoisted(() => ({ planTravelRoute: vi.fn() }));
vi.mock('./api', () => api);

import {
  requestCurrentCoordinates,
  ROUTE_UNAVAILABLE_MESSAGE,
  useTravelRoute,
} from './useTravelRoute';

const route = {
  provider: 'AMAP',
  travelMode: 'DRIVING' as const,
  durationMinutes: 20,
  suggestedDepartureAt: '2026-08-08T10:40:00+08:00',
  source: 'AMAP_ROUTE',
  dataTime: '2026-08-08T10:00:00+08:00',
  expiresAt: '2026-08-08T10:15:00+08:00',
  isExpired: false,
  degraded: false,
  fallbackType: null,
};

function geolocationSuccess(longitude = 112.9388146, latitude = 28.2282085) {
  const getCurrentPosition = vi.fn((success: PositionCallback) =>
    success({ coords: { longitude, latitude } } as GeolocationPosition),
  );
  vi.stubGlobal('navigator', { geolocation: { getCurrentPosition } });
  return getCurrentPosition;
}

describe('useTravelRoute', () => {
  beforeEach(() => {
    api.planTravelRoute.mockReset();
    api.planTravelRoute.mockResolvedValue(route);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('从浏览器读取原始坐标并只提交一次正式路线请求', async () => {
    const getCurrentPosition = geolocationSuccess();
    const { result } = renderHook(() => useTravelRoute('90001'));
    let outcome = '';
    await act(async () => {
      outcome = await result.current.plan('DRIVING', true);
    });

    expect(outcome).toBe('success');
    expect(getCurrentPosition).toHaveBeenCalledOnce();
    expect(api.planTravelRoute).toHaveBeenCalledOnce();
    expect(api.planTravelRoute).toHaveBeenCalledWith(
      '90001',
      {
        longitude: 112.9388146,
        latitude: 28.2282085,
        travelMode: 'DRIVING',
        thirdPartySharingConfirmed: true,
      },
      expect.any(AbortSignal),
    );
    expect(result.current.route).toEqual(route);
    expect(result.current).not.toHaveProperty('longitude');
    expect(result.current).not.toHaveProperty('latitude');
  });

  it('未确认共享时不申请定位也不请求后端', async () => {
    const getCurrentPosition = geolocationSuccess();
    const { result } = renderHook(() => useTravelRoute('90001'));
    await expect(result.current.plan('DRIVING', false)).resolves.toBe('failed');
    expect(getCurrentPosition).not.toHaveBeenCalled();
    expect(api.planTravelRoute).not.toHaveBeenCalled();
  });

  it('定位拒绝、浏览器无定位和非法坐标统一显示路线暂不可用', async () => {
    const cases = [
      {
        geolocation: {
          getCurrentPosition: (_success: PositionCallback, failure: PositionErrorCallback) =>
            failure({ code: 1 } as GeolocationPositionError),
        },
      },
      {},
      {
        geolocation: {
          getCurrentPosition: (success: PositionCallback) =>
            success({ coords: { longitude: 181, latitude: 28 } } as GeolocationPosition),
        },
      },
    ];

    for (const navigatorValue of cases) {
      vi.stubGlobal('navigator', navigatorValue);
      const { result, unmount } = renderHook(() => useTravelRoute('90001'));
      await act(async () => {
        await result.current.plan('WALKING', true);
      });
      expect(result.current.notice).toBe(ROUTE_UNAVAILABLE_MESSAGE);
      expect(api.planTravelRoute).not.toHaveBeenCalled();
      unmount();
      vi.unstubAllGlobals();
    }
  });

  it('规划期间禁止重复提交', async () => {
    let resolveLocation: PositionCallback = () => undefined;
    const getCurrentPosition = vi.fn((success: PositionCallback) => {
      resolveLocation = success;
    });
    vi.stubGlobal('navigator', { geolocation: { getCurrentPosition } });
    const { result } = renderHook(() => useTravelRoute('90001'));

    let first: Promise<string>;
    act(() => {
      first = result.current.plan('DRIVING', true);
    });
    await waitFor(() => expect(result.current.phase).toBe('locating'));
    await expect(result.current.plan('WALKING', true)).resolves.toBe('failed');
    expect(getCurrentPosition).toHaveBeenCalledOnce();

    await act(async () => {
      resolveLocation({
        coords: { longitude: 112.9388146, latitude: 28.2282085 },
      } as GeolocationPosition);
      await first!;
    });
    expect(api.planTravelRoute).toHaveBeenCalledOnce();
  });

  it.each([
    new ApiError('network', { kind: 'NETWORK', isResultUnknown: true }),
    new ApiError('timeout', { kind: 'TIMEOUT', isResultUnknown: true }),
    new ApiError('unavailable', { kind: 'HTTP', status: 503, code: 307001 }),
  ])('请求失败后不自动重发', async (error) => {
    geolocationSuccess();
    api.planTravelRoute.mockRejectedValue(error);
    const { result } = renderHook(() => useTravelRoute('90001'));
    await act(async () => {
      await result.current.plan('DRIVING', true);
    });
    expect(api.planTravelRoute).toHaveBeenCalledOnce();
    expect(result.current.notice).toBe(ROUTE_UNAVAILABLE_MESSAGE);
  });

  it('任务不存在时通知页面重新查询原 taskId', async () => {
    geolocationSuccess();
    api.planTravelRoute.mockRejectedValue(
      new ApiError('missing', { kind: 'HTTP', status: 404, code: 207001 }),
    );
    const { result } = renderHook(() => useTravelRoute('90001'));
    let outcome = '';
    await act(async () => {
      outcome = await result.current.plan('DRIVING', true);
    });
    expect(outcome).toBe('task-unavailable');
  });

  it('页面卸载后忽略迟到的定位结果', async () => {
    let resolveLocation: PositionCallback = () => undefined;
    vi.stubGlobal('navigator', {
      geolocation: {
        getCurrentPosition: (success: PositionCallback) => {
          resolveLocation = success;
        },
      },
    });
    const { result, unmount } = renderHook(() => useTravelRoute('90001'));
    let planning: Promise<string>;
    act(() => {
      planning = result.current.plan('DRIVING', true);
    });
    unmount();
    await act(async () => {
      resolveLocation({
        coords: { longitude: 112.9388146, latitude: 28.2282085 },
      } as GeolocationPosition);
      await planning!;
    });
    expect(api.planTravelRoute).not.toHaveBeenCalled();
  });
});

describe('requestCurrentCoordinates', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('不截断浏览器返回的小数', async () => {
    geolocationSuccess(112.9388146, 28.2282085);
    await expect(requestCurrentCoordinates()).resolves.toEqual({
      longitude: 112.9388146,
      latitude: 28.2282085,
    });
  });
});
