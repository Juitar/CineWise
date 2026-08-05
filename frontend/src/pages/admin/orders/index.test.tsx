import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/ApiError';
import { mockDetailMap, mockPageNormal } from './mock';
import AdminOrdersPage from './index';

const adminHookMocks = vi.hoisted(() => ({
  useAdminOrderDetail: vi.fn(),
  useAdminOrders: vi.fn(),
}));

vi.mock('../../../modules/admin/hooks', () => adminHookMocks);

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

describe('AdminOrdersPage', () => {
  beforeEach(() => {
    adminHookMocks.useAdminOrders.mockReturnValue({
      data: mockPageNormal,
      error: null,
      isLoading: false,
      isRefreshing: false,
      retry: vi.fn(),
    });
    adminHookMocks.useAdminOrderDetail.mockImplementation((orderNo: string | null) => ({
      data: orderNo ? (mockDetailMap[orderNo] ?? null) : null,
      error: null,
      isLoading: false,
      retry: vi.fn(),
    }));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('点击不同订单时按订单号查询并打开对应真实详情容器', () => {
    render(<AdminOrdersPage />);

    const viewDetailButtons = screen.getAllByRole('button', { name: '查看详情' });
    fireEvent.click(viewDetailButtons[0]);
    expect(adminHookMocks.useAdminOrderDetail).toHaveBeenLastCalledWith('CW-PENDING-1001');
    expect(screen.getByText('基本信息')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    fireEvent.click(viewDetailButtons[1]);
    expect(adminHookMocks.useAdminOrderDetail).toHaveBeenLastCalledWith('CW-PAID-1002');
    expect(screen.getByText('TICKET-PAID-1002')).toBeInTheDocument();
  });

  it('301002 保留已有列表、traceId 和手动重试入口', () => {
    const retry = vi.fn();
    adminHookMocks.useAdminOrders.mockReturnValue({
      data: mockPageNormal,
      error: new ApiError('directory unavailable', {
        code: 301002,
        kind: 'HTTP',
        status: 503,
        traceId: 'trace-admin-301002',
      }),
      isLoading: false,
      isRefreshing: false,
      retry,
    });

    render(<AdminOrdersPage />);

    expect(screen.getByText('用户信息查询暂不可用')).toBeInTheDocument();
    expect(screen.getByText('TraceId: trace-admin-301002')).toBeInTheDocument();
    expect(screen.getAllByText('CW-PENDING-1001').length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: '手动重试' }));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('201010 保留筛选区但不展示旧列表或重试按钮', () => {
    adminHookMocks.useAdminOrders.mockReturnValue({
      data: mockPageNormal,
      error: new ApiError('too broad', { code: 201010, kind: 'HTTP', status: 400 }),
      isLoading: false,
      isRefreshing: false,
      retry: vi.fn(),
    });

    render(<AdminOrdersPage />);

    expect(screen.getByText('用户查询条件过宽')).toBeInTheDocument();
    expect(screen.queryByText('CW-PENDING-1001')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '手动重试' })).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText('请输入用户ID或邮箱关键字')).toBeInTheDocument();
  });
});
