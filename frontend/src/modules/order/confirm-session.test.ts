import { renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

  afterEach(() => {
    vi.restoreAllMocks();
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

  it('HTTP 演示地址缺少 randomUUID 时使用 getRandomValues 生成 UUID', () => {
    let callCount = 0;
    vi.stubGlobal('crypto', {
      getRandomValues: vi.fn((bytes: Uint8Array) => {
        bytes.fill(callCount++);
        return bytes;
      }),
    });

    const session = getConfirmOrderSession('show-http', ['seat-1']);

    expect(session.clientRequestId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    expect(session.idempotencyKey).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    expect(session.clientRequestId).not.toBe(session.idempotencyKey);
  });

  it('两个 Crypto API 都缺少时仍生成合法且不重复的 UUID 兜底值', () => {
    vi.stubGlobal('crypto', {});

    const first = getConfirmOrderSession('show-fallback-1', ['seat-1']);
    const second = getConfirmOrderSession('show-fallback-2', ['seat-2']);
    const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

    expect(first.clientRequestId).toMatch(uuidPattern);
    expect(first.idempotencyKey).toMatch(uuidPattern);
    expect(second.clientRequestId).toMatch(uuidPattern);
    expect(second.idempotencyKey).toMatch(uuidPattern);
    expect(
      new Set([
        first.clientRequestId,
        first.idempotencyKey,
        second.clientRequestId,
        second.idempotencyKey,
      ]).size,
    ).toBe(4);
  });

  it('会话存储受限时在当前页面复用建单标识和未知状态', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    const first = getConfirmOrderSession('show-storage', ['seat-1']);
    const second = getConfirmOrderSession('show-storage', ['seat-1']);
    markConfirmOrderUnknown('show-storage', ['seat-1']);
    const recovered = getConfirmOrderSession('show-storage', ['seat-1']);

    expect(second).toEqual(first);
    expect(recovered).toMatchObject({ ...first, isResultUnknown: true });
    expect(() => clearConfirmOrderSession('show-storage', ['seat-1'])).not.toThrow();
  });
});
