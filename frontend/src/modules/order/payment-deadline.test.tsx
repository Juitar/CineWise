import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  PAYMENT_DEADLINE_FALLBACK,
  PAYMENT_DEADLINE_REACHED,
  resolvePaymentDeadline,
  usePaymentDeadline,
} from './payment-deadline';

describe('支付期限展示', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-10T05:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('根据服务端截止时间计算跨小时剩余时长', () => {
    expect(resolvePaymentDeadline('2026-08-10T14:02:03+08:00', Date.now())).toEqual({
      text: '1小时02分03秒',
      remainingSeconds: 3723,
      hasReachedDeadline: false,
    });
  });

  it('缺失或非法时间不回退到固定十五分钟', () => {
    expect(resolvePaymentDeadline(undefined, Date.now()).text).toBe(PAYMENT_DEADLINE_FALLBACK);
    expect(resolvePaymentDeadline('2026-02-31T14:00:00+08:00', Date.now()).text).toBe(
      PAYMENT_DEADLINE_FALLBACK,
    );
  });

  it('到达或超过截止时间只提示刷新服务端状态', () => {
    expect(resolvePaymentDeadline('2026-08-10T13:00:00+08:00', Date.now())).toEqual({
      text: PAYMENT_DEADLINE_REACHED,
      remainingSeconds: 0,
      hasReachedDeadline: true,
    });
  });

  it('按秒更新并在到达截止时间后停止', () => {
    const { result } = renderHook(() => usePaymentDeadline('2026-08-10T13:00:02+08:00'));
    expect(result.current.text).toBe('0分02秒');

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(result.current.text).toBe('0分01秒');

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(result.current.text).toBe(PAYMENT_DEADLINE_REACHED);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('停用时不创建定时器', () => {
    const { result } = renderHook(() => usePaymentDeadline('2026-08-10T13:10:00+08:00', false));
    expect(result.current.text).toBe('10分00秒');
    expect(vi.getTimerCount()).toBe(0);
  });
});
