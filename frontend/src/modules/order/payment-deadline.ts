import { useEffect, useMemo, useState } from 'react';
import { parseOrderDateTime } from './formatters';

export const PAYMENT_DEADLINE_FALLBACK = '支付期限以订单信息为准';
export const PAYMENT_DEADLINE_REACHED = '已到支付截止时间，请刷新订单状态';

export interface PaymentDeadlineView {
  text: string;
  remainingSeconds: number | null;
  hasReachedDeadline: boolean;
}

function formatRemainingSeconds(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const minuteText = minutes.toString().padStart(2, '0');
  const secondText = seconds.toString().padStart(2, '0');
  if (hours > 0) {
    return `${hours}小时${minuteText}分${secondText}秒`;
  }
  return `${minutes}分${secondText}秒`;
}

/**
 * 根据服务端订单截止时间生成只读支付期限视图。
 *
 * 本地时间只能驱动文案，不能据此把订单改成 EXPIRED 或触发任何交易写操作；
 * 到点后调用方应刷新服务端订单，最终状态仍由后端决定。
 */
export function resolvePaymentDeadline(
  expireTime: string | null | undefined,
  nowMillis: number,
): PaymentDeadlineView {
  const deadline = parseOrderDateTime(expireTime);
  if (!deadline) {
    return {
      text: PAYMENT_DEADLINE_FALLBACK,
      remainingSeconds: null,
      hasReachedDeadline: false,
    };
  }
  const remainingMillis = deadline.getTime() - nowMillis;
  if (remainingMillis <= 0) {
    return {
      text: PAYMENT_DEADLINE_REACHED,
      remainingSeconds: 0,
      hasReachedDeadline: true,
    };
  }
  const remainingSeconds = Math.ceil(remainingMillis / 1000);
  return {
    text: formatRemainingSeconds(remainingSeconds),
    remainingSeconds,
    hasReachedDeadline: false,
  };
}

/**
 * 按秒更新支付期限展示并在到达截止时间后停止定时器。
 *
 * 页面卸载、订单切换或倒计时停用时会清理定时器；本 Hook 不查询接口，也不执行支付。
 */
export function usePaymentDeadline(
  expireTime: string | null | undefined,
  enabled = true,
): PaymentDeadlineView {
  const [nowMillis, setNowMillis] = useState(() => Date.now());
  const deadlineView = useMemo(
    () => resolvePaymentDeadline(expireTime, nowMillis),
    [expireTime, nowMillis],
  );

  useEffect(() => {
    if (!enabled || deadlineView.remainingSeconds === null || deadlineView.hasReachedDeadline) {
      return undefined;
    }
    const timer = window.setTimeout(() => setNowMillis(Date.now()), 1000);
    return () => window.clearTimeout(timer);
  }, [deadlineView.hasReachedDeadline, deadlineView.remainingSeconds, enabled, expireTime]);

  return deadlineView;
}
