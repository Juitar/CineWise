import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearWriteOperationSession,
  getWriteOperationSession,
  markWriteResultUnknown,
} from './operation-session';

describe('订单写操作稳定恢复会话', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.stubGlobal('crypto', {
      randomUUID: vi.fn().mockReturnValueOnce('key-1').mockReturnValueOnce('request-1'),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('退款会话在刷新式重读时沿用相同幂等标识', () => {
    const first = getWriteOperationSession('refund', 'CW1');
    const second = getWriteOperationSession('refund', 'CW1');
    expect(first).toEqual(second);
    expect(first.idempotencyKey).toBe('key-1');
    expect(first.clientRequestId).toBe('request-1');
  });

  it('结果未知标记可恢复且明确完成后清除', () => {
    markWriteResultUnknown('payment', 'CW2');
    expect(getWriteOperationSession('payment', 'CW2').resultUnknown).toBe(true);
    clearWriteOperationSession('payment', 'CW2');
    expect(sessionStorage.length).toBe(0);
  });

  it('HTTP 环境缺少 randomUUID 时使用 getRandomValues', () => {
    const bytes = new Uint8Array(16).fill(0xab);
    vi.stubGlobal('crypto', {
      getRandomValues: (target: Uint8Array) => {
        target.set(bytes);
        return target;
      },
    });

    const session = getWriteOperationSession('payment', 'CW3');
    expect(session.idempotencyKey).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
  });

  it('Crypto API 不可用时仍生成合法且不重复的兜底标识', () => {
    vi.stubGlobal('crypto', undefined);

    const first = getWriteOperationSession('cancel', 'CW4');
    const second = getWriteOperationSession('refund', 'CW5');
    expect(first.idempotencyKey).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    expect(second.idempotencyKey).not.toBe(first.idempotencyKey);
    expect(second.clientRequestId).not.toBe(second.idempotencyKey);
  });

  it('会话存储受限时返回内存会话而不抛出异常', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    expect(() => getWriteOperationSession('payment', 'CW6')).not.toThrow();
  });
});
