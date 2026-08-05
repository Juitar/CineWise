import { describe, expect, it } from 'vitest';
import { calculateOrderTotalAmount } from './money';

describe('订单金额工具', () => {
  it('按分精确计算多座金额', () => {
    expect(calculateOrderTotalAmount('39.90', 3)).toBe('119.70');
    expect(calculateOrderTotalAmount('39', 2)).toBe('78.00');
  });

  it('非法价格、零座位和非安全数量返回安全降级值', () => {
    expect(calculateOrderTotalAmount('39.999', 1)).toBe('0.00');
    expect(calculateOrderTotalAmount('39.00', 0)).toBe('0.00');
    expect(calculateOrderTotalAmount('39.00', Number.MAX_SAFE_INTEGER + 1)).toBe('0.00');
  });
});
