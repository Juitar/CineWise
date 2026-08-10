import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../shared/api/ApiError';
import { planTravelRoute } from './api';
import { MAX_MANUAL_PLACE_LENGTH, type TravelMode, type TravelRoute } from './types';

export const ROUTE_UNAVAILABLE_MESSAGE = '路线暂不可用';
export const CURRENT_LOCATION_UNAVAILABLE_MESSAGE = '当前站点无法获取当前位置，请手动输入出发地点';

export interface CurrentCoordinates {
  longitude: number;
  latitude: number;
}

export type TravelRoutePhase = 'idle' | 'locating' | 'planning';
export type TravelRoutePlanOutcome = 'success' | 'failed' | 'task-unavailable';

class BrowserLocationUnavailable extends Error {
  constructor(message: string, readonly insecureContext = false) {
    super(message);
  }
}

function validCoordinates(longitude: number, latitude: number): boolean {
  return (
    Number.isFinite(longitude) &&
    longitude >= -180 &&
    longitude <= 180 &&
    Number.isFinite(latitude) &&
    latitude >= -90 &&
    latitude <= 90
  );
}

/**
 * 只读取一次浏览器当前位置。坐标仅通过 Promise 返回给当前调用栈，不写入任何存储或日志。
 */
export function requestCurrentCoordinates(): Promise<CurrentCoordinates> {
  return new Promise((resolve, reject) => {
    if (typeof window !== 'undefined' && window.isSecureContext === false) {
      reject(new BrowserLocationUnavailable(CURRENT_LOCATION_UNAVAILABLE_MESSAGE, true));
      return;
    }
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      reject(new BrowserLocationUnavailable('当前浏览器不支持定位，请手动输入城市。'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { longitude, latitude } = position.coords;
        if (!validCoordinates(longitude, latitude)) {
          reject(new BrowserLocationUnavailable('当前设备未提供有效位置，请手动输入城市。'));
          return;
        }
        resolve({ longitude, latitude });
      },
      (error) => {
        let message = '未能获取当前位置，请手动输入城市。';
        if (error.code === error.PERMISSION_DENIED)
          message = '浏览器未授予定位权限，请允许定位后重试。';
        else if (error.code === error.POSITION_UNAVAILABLE)
          message = '当前设备无法提供定位，请手动输入城市。';
        else if (error.code === error.TIMEOUT) message = '定位超时，请手动输入城市。';
        reject(new BrowserLocationUnavailable(message));
      },
      // 城市确认只需要市级精度。复用近期位置能显著减少桌面浏览器首次定位卡住的情况。
      { enableHighAccuracy: false, maximumAge: 5 * 60 * 1000, timeout: 15_000 },
    );
  });
}

function isTaskUnavailable(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.code === 207001);
}

function routeFailureNotice(error: unknown): string {
  if (error instanceof BrowserLocationUnavailable && error.insecureContext) {
    return CURRENT_LOCATION_UNAVAILABLE_MESSAGE;
  }
  if (error instanceof BrowserLocationUnavailable) return error.message;
  if (error instanceof ApiError && error.code === 107004) {
    return '地点无法唯一确定，请补充更具体的地址';
  }
  if (error instanceof ApiError && error.code === 107005) {
    return '请提供具体的地点或地址';
  }
  return ROUTE_UNAVAILABLE_MESSAGE;
}

/**
 * 管理一次性定位或地点输入和路线 POST。坐标及地点文本始终只存在当前调用栈，Hook state 不保存它们。
 */
export function useTravelRoute(taskId: string) {
  const [route, setRoute] = useState<TravelRoute | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [phase, setPhase] = useState<TravelRoutePhase>('idle');
  const attemptRef = useRef(0);
  const runningRef = useRef(false);
  const requestRef = useRef<AbortController | null>(null);

  useEffect(() => {
    attemptRef.current += 1;
    runningRef.current = false;
    requestRef.current?.abort();
    requestRef.current = null;
    setRoute(null);
    setNotice(null);
    setPhase('idle');
    return () => {
      attemptRef.current += 1;
      runningRef.current = false;
      requestRef.current?.abort();
      requestRef.current = null;
    };
  }, [taskId]);

  const plan = useCallback(
    async (travelMode: TravelMode, sharingConfirmed: boolean): Promise<TravelRoutePlanOutcome> => {
      if (runningRef.current || !sharingConfirmed) return 'failed';
      runningRef.current = true;
      const attempt = ++attemptRef.current;
      setRoute(null);
      setNotice(null);
      setPhase('locating');
      let controller: AbortController | null = null;
      try {
        const coordinates = await requestCurrentCoordinates();
        if (attemptRef.current !== attempt) return 'failed';
        controller = new AbortController();
        requestRef.current = controller;
        setPhase('planning');
        const nextRoute = await planTravelRoute(
          taskId,
          {
            originType: 'CURRENT_LOCATION',
            longitude: coordinates.longitude,
            latitude: coordinates.latitude,
            travelMode,
            thirdPartySharingConfirmed: true,
          },
          controller.signal,
        );
        if (attemptRef.current !== attempt) return 'failed';
        setRoute(nextRoute);
        return 'success';
      } catch (error) {
        if (attemptRef.current !== attempt) return 'failed';
        setNotice(routeFailureNotice(error));
        return isTaskUnavailable(error) ? 'task-unavailable' : 'failed';
      } finally {
        if (attemptRef.current === attempt) {
          runningRef.current = false;
          if (requestRef.current === controller) requestRef.current = null;
          setPhase('idle');
        }
      }
    },
    [taskId],
  );

  const planFromManualPlace = useCallback(
    async (
      placeText: string,
      travelMode: TravelMode,
      sharingConfirmed: boolean,
    ): Promise<TravelRoutePlanOutcome> => {
      const normalizedPlaceText = placeText.trim();
      if (runningRef.current || !sharingConfirmed || normalizedPlaceText.length === 0)
        return 'failed';
      if (normalizedPlaceText.length > MAX_MANUAL_PLACE_LENGTH) {
        setNotice('地点不能超过 200 个字符');
        return 'failed';
      }
      runningRef.current = true;
      const attempt = ++attemptRef.current;
      const controller = new AbortController();
      requestRef.current = controller;
      setRoute(null);
      setNotice(null);
      setPhase('planning');
      try {
        const nextRoute = await planTravelRoute(
          taskId,
          {
            originType: 'MANUAL_PLACE',
            placeText: normalizedPlaceText,
            travelMode,
            thirdPartySharingConfirmed: true,
          },
          controller.signal,
        );
        if (attemptRef.current !== attempt) return 'failed';
        setRoute(nextRoute);
        return 'success';
      } catch (error) {
        if (attemptRef.current !== attempt) return 'failed';
        setNotice(routeFailureNotice(error));
        return isTaskUnavailable(error) ? 'task-unavailable' : 'failed';
      } finally {
        if (attemptRef.current === attempt) {
          runningRef.current = false;
          if (requestRef.current === controller) requestRef.current = null;
          setPhase('idle');
        }
      }
    },
    [taskId],
  );

  return {
    route,
    notice,
    phase,
    isPlanning: phase !== 'idle',
    plan,
    planFromManualPlace,
  };
}
