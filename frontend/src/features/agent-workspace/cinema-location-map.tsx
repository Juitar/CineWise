import { Spin } from 'antd';
import React, { useEffect, useRef, useState } from 'react';

import { requestCurrentCoordinates } from '../../modules/travel/useTravelRoute';
import { resolveBrowserCity } from '../../modules/agent/api';

type Coordinates = [number, number];
type DeviceCoordinates = { longitude: number; latitude: number };

interface AMapMarker {
  getPosition(): { getLng(): number; getLat(): number };
}

interface AMapInstance {
  add(overlays: AMapMarker[]): void;
  destroy(): void;
  setFitView(overlays: AMapMarker[], immediately?: boolean, padding?: number[]): void;
}

interface AMapApi {
  GeometryUtil?: { distance(first: Coordinates, second: Coordinates): number };
  Map: new (
    container: HTMLElement,
    options: {
      center: Coordinates;
      viewMode: '2D' | '3D';
      zoom: number;
    },
  ) => AMapInstance;
  Marker: new (options: {
    label?: { content: string; direction: 'top' };
    position: Coordinates;
    title: string;
  }) => AMapMarker;
}

declare global {
  interface Window {
    AMap?: AMapApi;
    _AMapSecurityConfig?: { securityJsCode: string };
    __cinewiseAmapReady?: () => void;
  }
}

let amapPromise: Promise<AMapApi> | null = null;
const deviceLocationBySession = new Map<string, DeviceCoordinates>();
const AMAP_READY_CALLBACK = '__cinewiseAmapReady';

class CinemaLocationMapError extends Error {}

function withTimeout<T>(promise: Promise<T>, message: string, timeoutMs = 12_000): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => reject(new CinemaLocationMapError(message)), timeoutMs);
    promise.then(
      (value) => {
        window.clearTimeout(timer);
        resolve(value);
      },
      (error: unknown) => {
        window.clearTimeout(timer);
        reject(error);
      },
    );
  });
}

export function loadAmap(): Promise<AMapApi> {
  const key = process.env.AMAP_WEB_JS_KEY;
  const securityJsCode = process.env.AMAP_WEB_SECURITY_JS_CODE;
  if (!key || !securityJsCode) return Promise.reject(new CinemaLocationMapError('地图服务未配置'));
  if (window.AMap?.Map) return Promise.resolve(window.AMap);
  if (amapPromise) return amapPromise;

  const pendingLoad = new Promise<AMapApi>((resolve, reject) => {
    window._AMapSecurityConfig = { securityJsCode };
    let existing = document.getElementById('cinewise-amap-script') as HTMLScriptElement | null;
    // 失败的 script 节点不会再次触发 load/error；移除后允许本次进入详情页重新请求。
    if (existing && !window.AMap?.Map) {
      existing.remove();
      existing = null;
    }
    const resolveMap = () => {
      if (window.AMap?.Map) {
        resolve(window.AMap);
      } else {
        reject(new CinemaLocationMapError('地图初始化回调已执行，但未得到地图对象。'));
      }
    };
    if (existing) {
      if (window.AMap?.Map) {
        resolveMap();
        return;
      }
      existing.addEventListener('load', resolveMap, { once: true });
      existing.addEventListener(
        'error',
        () => reject(new CinemaLocationMapError('地图脚本请求失败，请检查网络、域名白名单或浏览器拦截设置。')),
        { once: true },
      );
      return;
    }
    const script = document.createElement('script');
    script.id = 'cinewise-amap-script';
    script.async = true;
    script.charset = 'utf-8';
    window[AMAP_READY_CALLBACK] = resolveMap;
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}&callback=${AMAP_READY_CALLBACK}`;
    // 高德回调是地图对象完成初始化的时点；若脚本已返回却未触发回调，不能无限等待。
    script.addEventListener('load', () => {
      window.setTimeout(() => {
        if (window.AMap?.Map) {
          resolveMap();
          return;
        }
        delete window[AMAP_READY_CALLBACK];
        reject(new CinemaLocationMapError('高德地图拒绝当前 Web Key，请检查 Key 状态和域名白名单。'));
      }, 0);
    }, { once: true });
    script.addEventListener('error', () => {
      delete window[AMAP_READY_CALLBACK];
      reject(new CinemaLocationMapError('地图脚本请求失败，请检查网络、域名白名单或浏览器拦截设置。'));
    }, {
      once: true,
    });
    document.head.appendChild(script);
  });
  // 高德异步脚本在部分网络下超过 8 秒仍会继续初始化，不能提前丢弃它的回调。
  amapPromise = pendingLoad;
  void amapPromise.catch(() => {
    // 网络或配置临时失败时允许用户点击后重新加载，而不是永久缓存失败结果。
    amapPromise = null;
  });
  return amapPromise;
}

async function requestDeviceCoordinates(sessionId: string): Promise<DeviceCoordinates> {
  const cached = deviceLocationBySession.get(sessionId);
  if (cached) return cached;
  const coordinates = await withTimeout(
    requestCurrentCoordinates(),
    '定位超时，请手动输入城市。',
  );
  deviceLocationBySession.set(sessionId, coordinates);
  return coordinates;
}

/** 仅向用户返回城市名；经纬度只留在当前页面内存中，供同一会话地图复用。 */
export async function resolveCityFromCurrentLocation(sessionId: string): Promise<string> {
  const coordinates = await requestDeviceCoordinates(sessionId);
  try {
    return await withTimeout(
      resolveBrowserCity(coordinates.longitude, coordinates.latitude),
      '城市识别超时，请手动输入城市。',
    );
  } catch {
    // 不向用户展示 Bean Validation、外部地图响应或其他后端内部错误文本。
    throw new CinemaLocationMapError('当前位置无法识别城市，请直接输入城市。');
  }
}

function calculateDistance(amap: AMapApi, first: Coordinates, second: Coordinates): number {
  if (amap.GeometryUtil) return amap.GeometryUtil.distance(first, second);
  const radians = Math.PI / 180;
  const latitudeDelta = (second[1] - first[1]) * radians;
  const longitudeDelta = (second[0] - first[0]) * radians;
  const a = Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(first[1] * radians) * Math.cos(second[1] * radians)
    * Math.sin(longitudeDelta / 2) ** 2;
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatDistance(distance: number): string {
  if (distance < 1000) return `直线距离约 ${Math.round(distance)} 米`;
  return `直线距离约 ${(distance / 1000).toFixed(1)} 公里`;
}

function mapFailureNotice(error: unknown): string {
  if (error instanceof CinemaLocationMapError) return error.message;
  if (error instanceof Error && error.message) return error.message;
  return '未能获取你的当前位置，请允许浏览器定位后重试。';
}

interface CinemaLocationMapProps {
  cinemaName: string;
  cinemaLoading: boolean;
  latitude: number | null | undefined;
  longitude: number | null | undefined;
  sessionId: string;
}

/** 影院静态坐标来自已加载的详情；用户坐标只留在当前页面内存中。 */
export function CinemaLocationMap({
  cinemaName,
  cinemaLoading,
  latitude,
  longitude,
  sessionId,
}: CinemaLocationMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<AMapInstance | null>(null);
  const [notice, setNotice] = useState('正在加载影院位置');
  const [loading, setLoading] = useState(false);
  const [shown, setShown] = useState(false);

  const showMap = async () => {
    if (!containerRef.current || loading) return;
    setLoading(true);
    setNotice('正在加载影院位置');
    try {
      if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
        throw new CinemaLocationMapError('影院暂未提供地图坐标');
      }
      const amap = await loadAmap();
      const cinemaCoordinates: Coordinates = [longitude, latitude];
      mapRef.current?.destroy();
      const map = new amap.Map(containerRef.current, {
        center: cinemaCoordinates,
        viewMode: '2D',
        zoom: 13,
      });
      const cinemaMarker = new amap.Marker({
        position: cinemaCoordinates,
        title: cinemaName,
        label: { content: cinemaName, direction: 'top' },
      });
      map.add([cinemaMarker]);
      mapRef.current = map;
      setShown(true);
      try {
        const position = await requestDeviceCoordinates(sessionId);
        const userMarker = new amap.Marker({
          position: [position.longitude, position.latitude],
          title: '你的位置',
          label: { content: '你的位置', direction: 'top' },
        });
        map.add([userMarker]);
        map.setFitView([userMarker, cinemaMarker], true, [42, 42, 42, 42]);
        setNotice(
          formatDistance(
            calculateDistance(amap, [position.longitude, position.latitude], cinemaCoordinates),
          ),
        );
      } catch {
        setNotice('已显示影院位置，未能获取当前位置距离。');
      }
    } catch (error) {
      mapRef.current?.destroy();
      mapRef.current = null;
      setShown(false);
      setNotice(mapFailureNotice(error));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // 进入详情页后直接展示影院并请求一次浏览器定位；拒绝定位只影响距离，不移除影院地图。
    if (cinemaLoading) return undefined;
    void showMap();
    return () => mapRef.current?.destroy();
  }, [cinemaLoading, cinemaName, latitude, longitude, sessionId]);

  return (
    <div className="agent-plan-location-map-shell">
      <div className="agent-plan-location-map" ref={containerRef} aria-label="影院位置地图">
        {cinemaLoading ? (
          <div className="agent-plan-location-map-skeleton" aria-label="正在加载影院位置" />
        ) : (
          !shown && <span>{loading ? <Spin size="small" /> : '影院位置'}</span>
        )}
      </div>
      <div className="agent-plan-location-map-action">
        <span>{cinemaLoading ? '正在加载影院位置' : notice}</span>
      </div>
    </div>
  );
}
