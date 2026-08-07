import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useAdminContentSync } from './hooks';

const api = vi.hoisted(() => ({
  queryContentSources: vi.fn(),
  queryContentSync: vi.fn(),
  requestContentSync: vi.fn(),
}));
vi.mock('./api', () => api);

const runningTask = {
  syncId: '1',
  clientRequestId: 'original-id',
  cityName: '长沙',
  status: 'RUNNING',
  startedAt: '2026-08-07T10:00:00+08:00',
  finishedAt: null,
  successCount: 0,
  failureCount: 0,
  failureCategory: null,
};

const pendingTask = { ...runningTask, status: 'PENDING' };

describe('内容同步恢复 Hook', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
    api.queryContentSources.mockReset().mockResolvedValue([]);
    api.queryContentSync.mockReset().mockResolvedValue(runningTask);
    api.requestContentSync.mockReset();
    vi.spyOn(crypto, 'randomUUID');
  });

  it('恢复到运行中任务后刷新页面仍使用原请求标识且不能新建同步', async () => {
    sessionStorage.setItem('cinewise:admin-content-sync', 'original-id');
    const first = renderHook(() => useAdminContentSync());
    await waitFor(() => expect(first.result.current.task?.status).toBe('RUNNING'));
    expect(sessionStorage.getItem('cinewise:admin-content-sync')).toBe('original-id');
    await first.result.current.submit('长沙');
    expect(api.requestContentSync).not.toHaveBeenCalled();
    expect(crypto.randomUUID).not.toHaveBeenCalled();
    first.unmount();

    renderHook(() => useAdminContentSync());
    await waitFor(() => expect(api.queryContentSync).toHaveBeenCalledTimes(2));
    expect(api.queryContentSync).toHaveBeenLastCalledWith('original-id');
    expect(api.requestContentSync).not.toHaveBeenCalled();
    expect(crypto.randomUUID).not.toHaveBeenCalled();
  });

  it('恢复到待执行任务后保留原请求标识，重新进入页面继续查询', async () => {
    sessionStorage.setItem('cinewise:admin-content-sync', 'pending-id');
    api.queryContentSync.mockResolvedValue(pendingTask);
    const first = renderHook(() => useAdminContentSync());
    await waitFor(() => expect(first.result.current.task?.status).toBe('PENDING'));
    expect(sessionStorage.getItem('cinewise:admin-content-sync')).toBe('pending-id');
    first.unmount();

    renderHook(() => useAdminContentSync());
    await waitFor(() => expect(api.queryContentSync).toHaveBeenCalledTimes(2));
    expect(api.queryContentSync).toHaveBeenLastCalledWith('pending-id');
    expect(api.requestContentSync).not.toHaveBeenCalled();
    expect(crypto.randomUUID).not.toHaveBeenCalled();
  });
});
