import { describe, expect, it, vi } from 'vitest';

import { queryContentSources, queryContentSync, requestContentSync } from './api';

const apiRequest = vi.hoisted(() => vi.fn());
vi.mock('../../shared/api/client', () => ({ apiRequest }));

describe('管理端内容同步 API', () => {
  it('使用 Controller 定义的路径和请求体', () => {
    void queryContentSources();
    void requestContentSync('request-1', '长沙');
    void queryContentSync('request-1');
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/v1/admin/content/sources', {
      signal: undefined,
    });
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/v1/admin/content/sync', {
      method: 'POST',
      body: { clientRequestId: 'request-1', cityName: '长沙' },
    });
    expect(apiRequest).toHaveBeenNthCalledWith(
      3,
      '/api/v1/admin/content/sync/by-request/request-1',
    );
  });
});
