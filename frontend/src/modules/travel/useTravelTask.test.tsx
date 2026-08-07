import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../shared/api/ApiError';

const api = vi.hoisted(() => ({
  getTravelAdvice: vi.fn(),
  getTravelTask: vi.fn(),
  getTravelTaskByOrder: vi.fn(),
  refreshTravelAdvice: vi.fn(),
  updateTravelReminder: vi.fn(),
}));
vi.mock('./api', () => api);

import { useTravelTask, useTravelTaskByOrder } from './useTravelTask';

const task = {
  taskId: '90001',
  status: 'READY' as const,
  triggerAt: '2026-08-07T18:00:00+08:00',
  version: 1,
  order: {
    orderId: '80001',
    orderNo: 'T001',
    showId: '70001',
    showStartTime: '2026-08-07T20:00:00+08:00',
  },
  movie: { movieId: '1', title: '测试影片', posterUrl: null, source: 'TEST', dataAt: null },
  cinema: {
    cinemaId: '2',
    name: '测试影院',
    area: null,
    address: null,
    source: 'TEST',
    dataAt: null,
    expiresAt: null,
    isExpired: false,
  },
};
const advice = {
  available: true,
  taskId: '90001',
  taskStatus: 'READY' as const,
  weather: null,
  advice: [{ type: 'TRANSPORT' as const, text: '提前到场' }],
  source: 'TEST',
  dataAt: null,
  expiresAt: null,
  isExpired: false,
  degraded: false,
  fallbackType: null,
};

describe('useTravelTask', () => {
  beforeEach(() => {
    Object.values(api).forEach((mock) => mock.mockReset());
    api.getTravelTask.mockResolvedValue(task);
    api.getTravelAdvice.mockResolvedValue(advice);
  });

  it('并行读取任务和建议', async () => {
    const { result } = renderHook(() => useTravelTask('90001'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.task).toEqual(task);
    expect(result.current.advice).toEqual(advice);
    expect(api.getTravelTask).toHaveBeenCalledOnce();
    expect(api.getTravelAdvice).toHaveBeenCalledOnce();
  });

  it('提醒写入结果未知后不重发 PUT，只能重新查询原任务', async () => {
    api.updateTravelReminder.mockRejectedValueOnce(
      new ApiError('timeout', { kind: 'TIMEOUT', isResultUnknown: true }),
    );
    const { result } = renderHook(() => useTravelTask('90001'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      expect(
        await result.current.updateReminder({
          triggerAt: '2026-08-07T18:30:00+08:00',
          version: 1,
        }),
      ).toBe(false);
    });
    expect(result.current.isReminderResultUnknown).toBe(true);
    expect(api.updateTravelReminder).toHaveBeenCalledOnce();

    await act(async () => {
      await result.current.reload();
    });
    expect(api.updateTravelReminder).toHaveBeenCalledOnce();
    expect(api.getTravelTask).toHaveBeenCalledTimes(2);
    expect(result.current.isReminderResultUnknown).toBe(false);
  });

  it('同步阻止重复刷新，并在 429 时保留已有建议', async () => {
    let rejectRefresh: (error: unknown) => void = () => undefined;
    api.refreshTravelAdvice.mockImplementationOnce(
      () =>
        new Promise((_resolve, reject) => {
          rejectRefresh = reject;
        }),
    );
    const { result } = renderHook(() => useTravelTask('90001'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let first: Promise<boolean> | undefined;
    await act(async () => {
      first = result.current.refresh();
      expect(await result.current.refresh()).toBe(false);
    });
    expect(api.refreshTravelAdvice).toHaveBeenCalledOnce();
    await act(async () => {
      rejectRefresh(new ApiError('too many', { kind: 'HTTP', status: 429, code: 107001 }));
      await first;
    });
    expect(result.current.advice).toEqual(advice);
    expect(result.current.notice).toContain('刷新过于频繁');
  });

  it.each([
    [409, 207002, '任务状态已经变化'],
    [503, 207004, '出行服务暂时不可用'],
  ])('刷新返回 %s 时保留已有建议并显示固定提示', async (status, code, message) => {
    api.refreshTravelAdvice.mockRejectedValueOnce(
      new ApiError('request failed', { kind: 'HTTP', status, code }),
    );
    const { result } = renderHook(() => useTravelTask('90001'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      expect(await result.current.refresh()).toBe(false);
    });
    expect(result.current.advice).toEqual(advice);
    expect(result.current.notice).toContain(message);
  });

  it('按订单查询失败时保留结构化错误', async () => {
    api.getTravelTaskByOrder.mockRejectedValueOnce(
      new ApiError('not found', { kind: 'HTTP', status: 404, code: 207001 }),
    );
    const { result } = renderHook(() => useTravelTaskByOrder());
    await act(async () => {
      await expect(result.current.find('80001')).rejects.toMatchObject({
        status: 404,
        code: 207001,
      });
    });
    expect(result.current.error).toMatchObject({ status: 404, code: 207001 });
  });
});
