import { beforeEach, describe, expect, it, vi } from 'vitest';
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
});
