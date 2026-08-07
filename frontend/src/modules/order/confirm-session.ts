import { useMemo } from 'react';
import { createOrderUuid } from './uuid';

export interface ConfirmOrderSession {
  clientRequestId: string;
  idempotencyKey: string;
  isResultUnknown: boolean;
}

function sessionKey(showId: string, seatIds: string[]): string {
  return `cinewise:order-confirm:${showId}:${[...seatIds].sort().join('_')}`;
}

/**
 * 创建建单幂等标识。
 *
 * `randomUUID` 只在安全上下文中保证可用；演示服务器可能通过 HTTP IP 访问，此时仍使用
 * `getRandomValues` 生成 RFC 4122 v4 标识，避免确认页在进入时因浏览器能力差异直接崩溃。
 */
function createSession(): ConfirmOrderSession {
  return {
    clientRequestId: createOrderUuid(),
    idempotencyKey: createOrderUuid(),
    isResultUnknown: false,
  };
}

/**
 * 读取或创建订单确认会话。
 *
 * 会话只保存建单所需的非敏感幂等标识；刷新页面或恢复查询时必须复用原标识。
 */
export function getConfirmOrderSession(showId: string, seatIds: string[]): ConfirmOrderSession {
  if (!showId || seatIds.length === 0) {
    return createSession();
  }
  const key = sessionKey(showId, seatIds);
  let stored: string | null = null;
  try {
    stored = sessionStorage.getItem(key);
  } catch {
    return createSession();
  }
  if (stored) {
    try {
      const parsed = JSON.parse(stored) as Partial<ConfirmOrderSession>;
      if (
        typeof parsed.clientRequestId === 'string' &&
        typeof parsed.idempotencyKey === 'string' &&
        typeof parsed.isResultUnknown === 'boolean'
      ) {
        return parsed as ConfirmOrderSession;
      }
    } catch {
      sessionStorage.removeItem(key);
    }
  }

  const created = createSession();
  try {
    sessionStorage.setItem(key, JSON.stringify(created));
  } catch {
    // 浏览器禁用会话存储时仍允许当前页面内复用 Hook 返回的标识。
  }
  return created;
}

/**
 * 根据当前路由中的场次和座位选择稳定的确认会话。
 *
 * 同一选择重渲染时复用标识，路由参数变化时切换到新作用域，避免串用上一笔订单的幂等键。
 */
export function useConfirmOrderSession(showId: string, seatIds: string[]): ConfirmOrderSession {
  const seatIdsKey = JSON.stringify([...seatIds].sort());
  return useMemo(
    () => getConfirmOrderSession(showId, JSON.parse(seatIdsKey) as string[]),
    [seatIdsKey, showId],
  );
}

/** 记录结果未知保护状态，阻止刷新后重新发起建单。 */
export function markConfirmOrderUnknown(showId: string, seatIds: string[]): void {
  if (!showId || seatIds.length === 0) {
    return;
  }
  const key = sessionKey(showId, seatIds);
  const session = getConfirmOrderSession(showId, seatIds);
  try {
    sessionStorage.setItem(key, JSON.stringify({ ...session, isResultUnknown: true }));
  } catch {
    // 当前页面仍由 Hook 维持未知保护，持久化不可用时不泄露或重组标识。
  }
}

/** 服务端明确返回建单结果后清理本次确认会话。 */
export function clearConfirmOrderSession(showId: string, seatIds: string[]): void {
  if (!showId || seatIds.length === 0) {
    return;
  }
  try {
    sessionStorage.removeItem(sessionKey(showId, seatIds));
  } catch {
    // 清理失败不改变服务端已经确认的订单结果。
  }
}
