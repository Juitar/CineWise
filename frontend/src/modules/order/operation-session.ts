export type WriteOperation = 'cancel' | 'payment' | 'refund';

export interface WriteOperationSession {
  idempotencyKey: string;
  clientRequestId?: string;
  resultUnknown: boolean;
}

function sessionKey(operation: WriteOperation, orderNo: string): string {
  return `cinewise:${operation}:${orderNo}`;
}

function createSession(operation: WriteOperation): WriteOperationSession {
  return {
    idempotencyKey: crypto.randomUUID(),
    clientRequestId: operation === 'refund' ? crypto.randomUUID() : undefined,
    resultUnknown: false,
  };
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
  const stored = sessionStorage.getItem(key);
  if (stored) {
    try {
      const parsed = JSON.parse(stored) as Partial<WriteOperationSession>;
      if (
        typeof parsed.idempotencyKey === 'string' &&
        typeof parsed.resultUnknown === 'boolean' &&
        (operation !== 'refund' || typeof parsed.clientRequestId === 'string')
      ) {
        return parsed as WriteOperationSession;
      }
    } catch {
      sessionStorage.removeItem(key);
    }
  }

  const created = createSession(operation);
  sessionStorage.setItem(key, JSON.stringify(created));
  return created;
}

/** 标记响应未知，刷新后仍禁止重新发送对应写请求。 */
export function markWriteResultUnknown(operation: WriteOperation, orderNo: string): void {
  const session = getWriteOperationSession(operation, orderNo);
  sessionStorage.setItem(
    sessionKey(operation, orderNo),
    JSON.stringify({ ...session, resultUnknown: true }),
  );
}

/** 服务端已明确返回结果后清理本次写操作会话。 */
export function clearWriteOperationSession(operation: WriteOperation, orderNo: string): void {
  sessionStorage.removeItem(sessionKey(operation, orderNo));
}
