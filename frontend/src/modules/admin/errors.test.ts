import { describe, expect, it } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import { resolveAdminDetailError, resolveAdminOrderErrorState } from './errors';

describe('管理订单错误映射', () => {
  it.each([
    [new ApiError('forbidden', { kind: 'HTTP', status: 403, code: 100403 }), 'FORBIDDEN'],
    [new ApiError('broad', { kind: 'HTTP', status: 400, code: 201010 }), 'QUERY_TOO_BROAD'],
    [
      new ApiError('directory', { kind: 'HTTP', status: 503, code: 301002 }),
      'DIRECTORY_UNAVAILABLE',
    ],
    [new ApiError('network', { kind: 'NETWORK' }), 'GENERAL_ERROR'],
  ] as const)('列表错误只按状态和错误码映射', (error, expected) => {
    expect(resolveAdminOrderErrorState(error)).toBe(expected);
  });

  it('详情 404 是明确结果，不允许无意义重试', () => {
    const result = resolveAdminDetailError(
      new ApiError('not found', { kind: 'HTTP', status: 404, code: 205001 }),
    );
    expect(result).toEqual({ canRetry: false, message: '订单不存在或无权访问' });
  });

  it('详情网络失败保留原订单号并允许手动重试', () => {
    expect(resolveAdminDetailError(new ApiError('offline', { kind: 'NETWORK' })).canRetry).toBe(
      true,
    );
  });
});
