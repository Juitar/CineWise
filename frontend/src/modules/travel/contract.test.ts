import { describe, expect, it } from 'vitest';
import adviceExpired from '../../../../backend/src/test/resources/fixtures/travel/c/advice-expired.json';
import adviceNormal from '../../../../backend/src/test/resources/fixtures/travel/c/advice-weather-normal.json';
import adviceUnavailable from '../../../../backend/src/test/resources/fixtures/travel/c/advice-weather-unavailable.json';
import taskCancelled from '../../../../backend/src/test/resources/fixtures/travel/c/task-detail-cancelled.json';
import taskSuccess from '../../../../backend/src/test/resources/fixtures/travel/c/task-detail-success.json';
import {
  parseTravelAdvice,
  parseTravelRoute,
  parseTravelTask,
  TravelContractError,
} from './contract';

const routeResult = {
  provider: 'AMAP',
  travelMode: 'DRIVING',
  durationMinutes: 20,
  suggestedDepartureAt: '2026-08-08T10:40:00+08:00',
  source: 'AMAP_ROUTE',
  dataTime: '2026-08-08T10:00:00+08:00',
  expiresAt: '2026-08-08T10:15:00+08:00',
  isExpired: false,
  degraded: false,
  fallbackType: null,
};

describe('出行 REST 契约', () => {
  it('直接解析 D 的正式任务夹具，并保持业务 ID 为字符串', () => {
    expect(parseTravelTask(taskSuccess.data)).toMatchObject({
      taskId: '90001',
      status: 'READY',
      order: { orderId: '80001', showId: '70001' },
      cinema: { isExpired: false },
    });
    expect(parseTravelTask(taskCancelled.data).status).toBe('CANCELLED');
  });

  it('解析正常、天气不可用和已失效建议', () => {
    expect(parseTravelAdvice(adviceNormal.data)).toMatchObject({
      available: true,
      source: 'AMAP_WEATHER',
      isExpired: false,
    });
    expect(parseTravelAdvice(adviceUnavailable.data)).toMatchObject({
      weather: null,
      degraded: true,
      fallbackType: 'NO_WEATHER',
    });
    expect(parseTravelAdvice(adviceExpired.data).isExpired).toBe(true);
  });

  it('拒绝非法 ID、未知建议类型和非法时间', () => {
    expect(() => parseTravelTask({ ...taskSuccess.data, taskId: 90001 })).toThrow(
      TravelContractError,
    );
    expect(() =>
      parseTravelAdvice({
        ...adviceNormal.data,
        advice: [{ type: 'ROUTE', text: '未经协议声明的路线' }],
      }),
    ).toThrow(TravelContractError);
    expect(() =>
      parseTravelTask({ ...taskSuccess.data, triggerAt: '2026-02-31T10:00:00+08:00' }),
    ).toThrow(TravelContractError);
  });

  it('忽略兼容 JSON 和未声明字段，不把它们带入页面 DTO', () => {
    const result = parseTravelAdvice({
      ...adviceNormal.data,
      weatherJson: '{"private":"value"}',
      adviceJson: '{"route":"fake"}',
      internalProviderId: 'secret',
    });
    expect(result).not.toHaveProperty('weatherJson');
    expect(result).not.toHaveProperty('adviceJson');
    expect(result).not.toHaveProperty('internalProviderId');
  });

  it('解析不含坐标的驾车或步行路线摘要', () => {
    expect(parseTravelRoute(routeResult)).toEqual(routeResult);
    expect(parseTravelRoute({ ...routeResult, travelMode: 'WALKING' }).travelMode).toBe('WALKING');
  });

  it('拒绝 TRANSIT、非法耗时和非法路线时间', () => {
    expect(() => parseTravelRoute({ ...routeResult, travelMode: 'TRANSIT' })).toThrow(
      TravelContractError,
    );
    expect(() => parseTravelRoute({ ...routeResult, durationMinutes: -1 })).toThrow(
      TravelContractError,
    );
    expect(() => parseTravelRoute({ ...routeResult, expiresAt: 'invalid' })).toThrow(
      TravelContractError,
    );
  });

  it('路线 DTO 忽略服务端未声明字段和坐标', () => {
    const route = parseTravelRoute({
      ...routeResult,
      longitude: 112.9388146,
      latitude: 28.2282085,
      geometry: 'private',
    });
    expect(route).not.toHaveProperty('longitude');
    expect(route).not.toHaveProperty('latitude');
    expect(route).not.toHaveProperty('geometry');
  });
});
