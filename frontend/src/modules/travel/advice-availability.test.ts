import { describe, expect, it } from 'vitest';

import { isTravelAdviceAvailable } from './advice-availability';

describe('出行建议生成时间', () => {
  const now = Date.parse('2026-08-08T10:00:00+08:00');

  it('开场前两小时外不展示出行建议入口', () => {
    expect(isTravelAdviceAvailable('2026-08-08T12:01:00+08:00', now)).toBe(false);
  });

  it('到达开场前两小时后允许查看出行建议', () => {
    expect(isTravelAdviceAvailable('2026-08-08T12:00:00+08:00', now)).toBe(true);
  });

  it('无效开场时间不展示入口', () => {
    expect(isTravelAdviceAvailable('invalid', now)).toBe(false);
  });
});
