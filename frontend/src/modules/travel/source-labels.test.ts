import { describe, expect, it } from 'vitest';
import { formatTravelSource } from './source-labels';

describe('出行来源中文显示', () => {
  it.each([
    ['NETSTART_MAOYAN', 'CONTENT', '猫眼'],
    ['AMAP_ROUTE', 'ROUTE', '高德路线'],
    ['AMAP_WEATHER', 'WEATHER', '高德天气'],
    ['DEMO_WEATHER_V1', 'WEATHER', '演示天气'],
  ] as const)('%s 显示为中文名称', (source, kind, label) => {
    expect(formatTravelSource(source, kind)).toBe(label);
  });

  it('未知来源不把内部编码直接展示给用户', () => {
    expect(formatTravelSource('UNKNOWN_PROVIDER', 'ROUTE')).toBe('来源待确认');
    expect(formatTravelSource(null, 'WEATHER')).toBe('来源待确认');
  });
});
