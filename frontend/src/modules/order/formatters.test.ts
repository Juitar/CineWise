import { describe, expect, it } from 'vitest';
import { formatOrderDateTime, parseOrderDateTime } from './formatters';

describe('订单业务时间格式化', () => {
  it('固定按 Asia/Shanghai 展示带偏移时间', () => {
    expect(formatOrderDateTime('2026-08-10T06:30:00Z')).toBe('2026-08-10 14:30');
    expect(formatOrderDateTime('2026-08-10T14:30:00+08:00')).toBe('2026-08-10 14:30');
    expect(formatOrderDateTime('2026-08-10T14:30:00')).toBe('2026-08-10 14:30');
  });

  it('缺失或非法时间返回明确降级文案', () => {
    expect(formatOrderDateTime(undefined)).toBe('时间信息暂不可用');
    expect(formatOrderDateTime('not-a-time')).toBe('时间信息暂不可用');
    expect(formatOrderDateTime('2026-02-31T14:30:00')).toBe('时间信息暂不可用');
    expect(formatOrderDateTime('2026-02-31T14:30:00+08:00')).toBe('时间信息暂不可用');
  });

  it('解析无偏移业务时间和带偏移时间为同一时间点', () => {
    expect(parseOrderDateTime('2026-08-10T14:30:00')?.toISOString()).toBe(
      '2026-08-10T06:30:00.000Z',
    );
    expect(parseOrderDateTime('2026-08-10T14:30:00+08:00')?.toISOString()).toBe(
      '2026-08-10T06:30:00.000Z',
    );
  });
});
