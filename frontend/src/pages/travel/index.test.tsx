import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../shared/api/ApiError';
import { setupTestEnvironment } from '../../features/test-utils';

const mocks = vi.hoisted(() => ({ historyPush: vi.fn(), travel: vi.fn() }));
vi.mock('umi', () => ({
  history: { push: mocks.historyPush },
  useParams: () => ({ taskId: '90001' }),
}));
vi.mock('../../modules/travel/useTravelTask', () => ({ useTravelTask: mocks.travel }));

import TravelPage from './index';

setupTestEnvironment();

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
    area: '岳麓区',
    address: '测试路 1 号',
    source: 'LIVE_CONTENT',
    dataAt: null,
    expiresAt: null,
    isExpired: false,
  },
};
const advice = {
  available: true,
  taskId: '90001',
  taskStatus: 'READY' as const,
  weather: { area: '岳麓区', condition: '多云', risk: '注意降雨' },
  advice: [{ type: 'TRANSPORT' as const, text: '提前到场' }],
  source: 'AMAP_WEATHER',
  dataAt: '2026-08-07T12:00:00+08:00',
  expiresAt: '2026-08-07T18:00:00+08:00',
  isExpired: false,
  degraded: false,
  fallbackType: null,
};

function state(overrides: Record<string, unknown> = {}) {
  return {
    task,
    advice,
    error: null,
    notice: null,
    isLoading: false,
    isRefreshing: false,
    isUpdatingReminder: false,
    isReminderResultUnknown: false,
    reload: vi.fn(),
    refresh: vi.fn(),
    updateReminder: vi.fn(),
    ...overrides,
  };
}

describe('观影出行建议页', () => {
  beforeEach(() => vi.clearAllMocks());

  it('加载期间显示加载状态', () => {
    mocks.travel.mockReturnValue(state({ task: null, advice: null, isLoading: true }));
    render(<TravelPage />);
    expect(screen.getByText('正在加载出行建议')).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveAttribute('aria-busy', 'true');
  });

  it('只展示服务端返回的天气、建议、来源和影院数据', () => {
    mocks.travel.mockReturnValue(state());
    render(<TravelPage />);
    expect(screen.getByText('提前到场')).toBeInTheDocument();
    expect(screen.getByText(/AMAP_WEATHER/)).toBeInTheDocument();
    expect(screen.getByText('测试路 1 号')).toBeInTheDocument();
    expect(screen.queryByText(/km|餐饮|路线预览/)).not.toBeInTheDocument();
  });

  it.each([
    ['建议失效', { advice: { ...advice, isExpired: true } }],
    ['任务取消', { task: { ...task, status: 'CANCELLED' } }],
  ])('%s 时禁用提醒更新和刷新', (_name, overrides) => {
    mocks.travel.mockReturnValue(state(overrides));
    render(<TravelPage />);
    expect(screen.getByRole('button', { name: '更新提醒时间' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '刷新建议' })).toBeDisabled();
  });

  it('建议未生成时显示空态并允许主动刷新', () => {
    const refresh = vi.fn();
    mocks.travel.mockReturnValue(
      state({ advice: { ...advice, available: false, weather: null, advice: [] }, refresh }),
    );
    render(<TravelPage />);
    expect(screen.getByText('建议尚未生成，可稍后主动刷新')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '刷新建议' }));
    expect(refresh).toHaveBeenCalledOnce();
  });

  it('天气降级时说明原因并继续展示通用建议', () => {
    mocks.travel.mockReturnValue(
      state({
        advice: {
          ...advice,
          weather: null,
          degraded: true,
          fallbackType: 'NO_WEATHER',
        },
      }),
    );
    render(<TravelPage />);
    expect(screen.getByText('部分动态数据暂不可用')).toBeInTheDocument();
    expect(screen.getByText('天气暂不可用，通用交通建议仍可查看')).toBeInTheDocument();
    expect(screen.getByText('提前到场')).toBeInTheDocument();
  });

  it('404 时返回订单列表', () => {
    mocks.travel.mockReturnValue(
      state({
        task: null,
        advice: null,
        error: new ApiError('not found', { kind: 'HTTP', status: 404, code: 207001 }),
      }),
    );
    render(<TravelPage />);
    fireEvent.click(screen.getByRole('button', { name: '返回订单' }));
    expect(mocks.historyPush).toHaveBeenCalledWith('/orders');
  });

  it('结果未知时只提供重新查询任务', () => {
    const reload = vi.fn();
    mocks.travel.mockReturnValue(state({ isReminderResultUnknown: true, reload }));
    render(<TravelPage />);
    expect(screen.getByRole('button', { name: '更新提醒时间' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '重新查询任务' }));
    expect(reload).toHaveBeenCalledOnce();
  });

  it('提交带业务时区的提醒时间和当前版本', () => {
    const updateReminder = vi.fn();
    mocks.travel.mockReturnValue(state({ updateReminder }));
    render(<TravelPage />);
    fireEvent.change(screen.getByLabelText('修改提醒时间'), {
      target: { value: '2026-08-07T18:30' },
    });
    fireEvent.click(screen.getByRole('button', { name: '更新提醒时间' }));
    expect(updateReminder).toHaveBeenCalledWith({
      triggerAt: '2026-08-07T18:30:00+08:00',
      version: 1,
    });
  });
});
