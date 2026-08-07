import { beforeEach, describe, expect, it, vi } from 'vitest';
import adviceNormal from '../../../../backend/src/test/resources/fixtures/travel/c/advice-weather-normal.json';
import reminderSuccess from '../../../../backend/src/test/resources/fixtures/travel/c/reminder-update-success.json';
import taskSuccess from '../../../../backend/src/test/resources/fixtures/travel/c/task-detail-success.json';

const client = vi.hoisted(() => ({ apiRequest: vi.fn() }));
vi.mock('../../shared/api/client', () => client);

import {
  getTravelAdvice,
  getTravelTask,
  getTravelTaskByOrder,
  refreshTravelAdvice,
  updateTravelReminder,
} from './api';

describe('出行 API', () => {
  beforeEach(() => client.apiRequest.mockReset());

  it('使用正式路径查询任务、订单任务和建议', async () => {
    const signal = new AbortController().signal;
    client.apiRequest
      .mockResolvedValueOnce(taskSuccess.data)
      .mockResolvedValueOnce(taskSuccess.data)
      .mockResolvedValueOnce(adviceNormal.data);

    await getTravelTask('90001', signal);
    await getTravelTaskByOrder('80001', signal);
    await getTravelAdvice('90001', signal);

    expect(client.apiRequest).toHaveBeenNthCalledWith(1, '/api/v1/travel/tasks/90001', { signal });
    expect(client.apiRequest).toHaveBeenNthCalledWith(2, '/api/v1/travel/tasks/by-order/80001', {
      signal,
    });
    expect(client.apiRequest).toHaveBeenNthCalledWith(3, '/api/v1/travel/tasks/90001/advice', {
      signal,
    });
  });

  it('刷新建议只发送一次 POST', async () => {
    client.apiRequest.mockResolvedValueOnce(adviceNormal.data);
    await refreshTravelAdvice('90001');
    expect(client.apiRequest).toHaveBeenCalledWith('/api/v1/travel/tasks/90001/advice/refresh', {
      method: 'POST',
    });
  });

  it('提醒更新同时传递 If-Match 和请求体版本', async () => {
    client.apiRequest.mockResolvedValueOnce(reminderSuccess.data);
    await expect(
      updateTravelReminder('90001', {
        triggerAt: '2026-08-07T18:00:00+08:00',
        version: 1,
      }),
    ).resolves.toMatchObject({ version: 2 });
    expect(client.apiRequest).toHaveBeenCalledWith('/api/v1/travel/tasks/90001/reminder', {
      method: 'PUT',
      headers: { 'If-Match': '"1"' },
      body: { triggerAt: '2026-08-07T18:00:00+08:00', version: 1 },
    });
  });
});
