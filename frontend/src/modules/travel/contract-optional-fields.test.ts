import { describe, expect, it } from 'vitest';
import { parseTravelAdvice } from './contract';

describe('travel advice optional fields', () => {
  it('accepts an AMAP response without fallbackType', () => {
    const advice = parseTravelAdvice({
      available: true,
      taskId: '90001',
      taskStatus: 'READY',
      weather: { area: '雨花区', condition: '多云（33℃）', risk: '提前出发' },
      advice: [{ type: 'WEATHER', text: '提前出发' }],
      source: 'AMAP_WEATHER',
      dataAt: '2026-08-08T15:34:18+08:00',
      expiresAt: '2026-08-08T15:54:57+08:00',
      isExpired: false,
      degraded: false,
    });

    expect(advice.fallbackType).toBeNull();
    expect(advice.weather?.condition).toBe('多云（33℃）');
  });

  it('accepts a pending response without weather or freshness fields', () => {
    const advice = parseTravelAdvice({
      available: false,
      taskId: '90001',
      taskStatus: 'PENDING',
      advice: [],
      isExpired: false,
      degraded: false,
    });

    expect(advice).toMatchObject({
      available: false,
      weather: null,
      source: null,
      dataAt: null,
      expiresAt: null,
      fallbackType: null,
    });
  });
});
