import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearConfirmOrderSession,
  getConfirmOrderSession,
  markConfirmOrderUnknown,
  useConfirmOrderSession,
} from './confirm-session';

describe('订单确认幂等会话', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.stubGlobal('crypto', {
      randomUUID: vi
        .fn()
        .mockReturnValueOnce('request-1')
        .mockReturnValueOnce('key-1')
        .mockReturnValueOnce('request-2')
        .mockReturnValueOnce('key-2'),
    });
  });

  it('相同场次与座位重载时复用稳定标识', () => {
    const first = getConfirmOrderSession('show-1', ['seat-2', 'seat-1']);
    const second = getConfirmOrderSession('show-1', ['seat-1', 'seat-2']);

    expect(second).toEqual(first);
    expect(first.clientRequestId).toBe('request-1');
    expect(first.idempotencyKey).toBe('key-1');
  });

  it('结果未知状态可跨刷新恢复，明确结果后清除', () => {
    getConfirmOrderSession('show-1', ['seat-1']);
    markConfirmOrderUnknown('show-1', ['seat-1']);

    expect(getConfirmOrderSession('show-1', ['seat-1']).isResultUnknown).toBe(true);
    clearConfirmOrderSession('show-1', ['seat-1']);
    expect(sessionStorage.length).toBe(0);
  });

  it('无效路由参数不写入持久会话', () => {
    getConfirmOrderSession('', []);
    expect(sessionStorage.length).toBe(0);
  });

  it('路由选择变化时切换幂等会话', () => {
    const { result, rerender } = renderHook(
      ({ showId, seatIds }: { showId: string; seatIds: string[] }) =>
        useConfirmOrderSession(showId, seatIds),
      { initialProps: { showId: 'show-1', seatIds: ['seat-1'] } },
    );
    expect(result.current.clientRequestId).toBe('request-1');

    rerender({ showId: 'show-2', seatIds: ['seat-2'] });
    expect(result.current.clientRequestId).toBe('request-2');
  });
});
