import { describe, expect, it } from 'vitest';
import { formatShowTime } from './formatters';

describe('场次时间展示', () => {
  it('按固定业务时区展示时分', () => {
    expect(formatShowTime('2026-08-10T14:30:00+08:00')).toBe('14:30');
    expect(formatShowTime('2026-08-10T14:30:00')).toBe('14:30');
  });

  it('非法或缺失时间使用明确降级文案', () => {
    expect(formatShowTime('2026-02-31T14:30:00')).toBe('时间待确认');
    expect(formatShowTime(undefined)).toBe('时间待确认');
  });
});
