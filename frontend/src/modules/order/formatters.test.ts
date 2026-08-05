import { describe, expect, it } from 'vitest';
import { formatOrderDateTime } from './formatters';

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
  });
});
