import { createOrderUuid } from './uuid';

export type WriteOperation = 'cancel' | 'payment' | 'refund';

export interface WriteOperationSession {
  idempotencyKey: string;
  clientRequestId?: string;
  resultUnknown: boolean;
}

function sessionKey(operation: WriteOperation, orderNo: string): string {
  return `cinewise:${operation}:${orderNo}`;
}

// sessionStorage 不可用时只在当前页面生命周期保存会话，确保结果未知恢复不会换用新幂等键。
const inMemorySessions = new Map<string, WriteOperationSession>();

function createSession(operation: WriteOperation): WriteOperationSession {
  return {
    idempotencyKey: createOrderUuid(),
    clientRequestId: operation === 'refund' ? createOrderUuid() : undefined,
    resultUnknown: false,
  };
}

function createInMemorySession(key: string, operation: WriteOperation): WriteOperationSession {
  const session = createSession(operation);
  inMemorySessions.set(key, session);
  return session;
}

/**
 * 读取或创建写操作恢复会话。
 *
 * 会话只保存非敏感幂等标识；模拟密码禁止进入 sessionStorage。
 */
export function getWriteOperationSession(
  operation: WriteOperation,
  orderNo: string,
): WriteOperationSession {
  const key = sessionKey(operation, orderNo);
  let stored: string | null = null;
  try {
    stored = sessionStorage.getItem(key);
  } catch {
    return inMemorySessions.get(key) ?? createInMemorySession(key, operation);
  }
  if (stored) {
    try {
      const parsed = JSON.parse(stored) as Partial<WriteOperationSession>;
      if (
        typeof parsed.idempotencyKey === 'string' &&
        typeof parsed.resultUnknown === 'boolean' &&
        (operation !== 'refund' || typeof parsed.clientRequestId === 'string')
      ) {
        const session = parsed as WriteOperationSession;
        inMemorySessions.set(key, session);
        return session;
      }
    } catch {
      try {
        sessionStorage.removeItem(key);
      } catch {
        // 受限浏览器无法清理损坏快照时，下面会创建当前页可用的新会话。
      }
    }
  }

  const created = inMemorySessions.get(key) ?? createInMemorySession(key, operation);
  try {
    sessionStorage.setItem(key, JSON.stringify(created));
  } catch {
    // 会话存储不可用时仍返回内存中的标识，避免交易页直接白屏。
  }
  return created;
}

/** 标记响应未知，刷新后仍禁止重新发送对应写请求。 */
export function markWriteResultUnknown(operation: WriteOperation, orderNo: string): void {
  const session = getWriteOperationSession(operation, orderNo);
  const resultUnknownSession = { ...session, resultUnknown: true };
  inMemorySessions.set(sessionKey(operation, orderNo), resultUnknownSession);
  try {
    sessionStorage.setItem(sessionKey(operation, orderNo), JSON.stringify(resultUnknownSession));
  } catch {
    // 结果未知状态仍由当前 Hook 保存；存储受限不应导致页面异常。
  }
}

/** 服务端已明确返回结果后清理本次写操作会话。 */
export function clearWriteOperationSession(operation: WriteOperation, orderNo: string): void {
  inMemorySessions.delete(sessionKey(operation, orderNo));
  try {
    sessionStorage.removeItem(sessionKey(operation, orderNo));
  } catch {
    // 清理失败不改变服务端已经确认的写操作结果。
  }
}
