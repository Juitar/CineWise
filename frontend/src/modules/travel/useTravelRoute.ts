import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../shared/api/ApiError';
import { planTravelRoute } from './api';
import type { TravelMode, TravelRoute } from './types';

export const ROUTE_UNAVAILABLE_MESSAGE = '路线暂不可用';

export interface CurrentCoordinates {
  longitude: number;
  latitude: number;
}

export type TravelRoutePhase = 'idle' | 'locating' | 'planning';
export type TravelRoutePlanOutcome = 'success' | 'failed' | 'task-unavailable';

class BrowserLocationUnavailable extends Error {}

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
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      reject(new BrowserLocationUnavailable());
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { longitude, latitude } = position.coords;
        if (!validCoordinates(longitude, latitude)) {
          reject(new BrowserLocationUnavailable());
          return;
        }
        resolve({ longitude, latitude });
      },
      () => reject(new BrowserLocationUnavailable()),
      { enableHighAccuracy: false, maximumAge: 0, timeout: 10_000 },
    );
  });
}

function isTaskUnavailable(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.code === 207001);
}

/**
 * 管理一次性定位和路线 POST。精确坐标始终是 plan() 的局部变量，Hook state 不保存坐标。
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
        setNotice(ROUTE_UNAVAILABLE_MESSAGE);
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
  };
}
