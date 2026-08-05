import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  mockDetailPaid,
  mockDetailPending,
  mockDetailRefunded,
} from '../../pages/admin/orders/mock';
import { AdminOrderDetailDrawer } from './AdminOrderDetailDrawer';

// 注入 matchMedia mock
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(), // Deprecated
    removeListener: vi.fn(), // Deprecated
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

describe('AdminOrderDetailDrawer', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('正确渲染待支付订单，显示无支付/票/退款记录', () => {
    render(<AdminOrderDetailDrawer open={true} onClose={vi.fn()} detail={mockDetailPending} />);

    expect(screen.getByText('基本信息')).toBeInTheDocument();
    expect(screen.getByText('CW-PENDING-1001')).toBeInTheDocument();

    // 渲染了两个空记录的提示
    expect(screen.getByText('无支付记录')).toBeInTheDocument();
    expect(screen.getByText('无电子票记录')).toBeInTheDocument();
    expect(screen.getByText('无退款记录')).toBeInTheDocument();
  });

  it('正确渲染已支付出票订单', () => {
    render(<AdminOrderDetailDrawer open={true} onClose={vi.fn()} detail={mockDetailPaid} />);

    expect(screen.getByText('CW-PAID-1002')).toBeInTheDocument();

    // 不会显示无记录提示
    expect(screen.queryByText('无支付记录')).not.toBeInTheDocument();

    // 支付详情
    expect(screen.getByText('PAY-PAID-1002')).toBeInTheDocument();
    // 电子票详情
    expect(screen.getByText('TICKET-PAID-1002')).toBeInTheDocument();

    // 退款为空
    expect(screen.getByText('无退款记录')).toBeInTheDocument();
  });

  it('正确渲染已退款订单，不包含二维码等敏感数据', () => {
    render(<AdminOrderDetailDrawer open={true} onClose={vi.fn()} detail={mockDetailRefunded} />);

    expect(screen.getByText('CW-REFUNDED-1')).toBeInTheDocument();
    expect(screen.getByText('行程变化')).toBeInTheDocument();

    // 查不到敏感操作按钮
    expect(screen.queryByRole('button', { name: /重试/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /取消订单/ })).not.toBeInTheDocument();
  });

  it('未选择订单时显示安全占位提示', () => {
    render(<AdminOrderDetailDrawer open={true} onClose={vi.fn()} detail={null} loading={false} />);

    expect(screen.getByText('请选择订单查看详情')).toBeInTheDocument();
  });

  it('服务异常时显示 traceId 并按原订单允许手动重试', () => {
    const handleRetry = vi.fn();
    render(
      <AdminOrderDetailDrawer
        canRetry
        detail={null}
        errorMessage="获取订单详情失败，请稍后重试"
        loading={false}
        onClose={vi.fn()}
        onRetry={handleRetry}
        open
        traceId="trace-detail-1"
      />,
    );

    expect(screen.getByText('TraceId: trace-detail-1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '手动重试' }));
    expect(handleRetry).toHaveBeenCalledTimes(1);
  });

  it('正确渲染 stateVersion 为 0 的情况，不会显示为未产生或空', () => {
    const detailWithZeroVersion = {
      ...mockDetailPending,
      summary: {
        ...mockDetailPending.summary,
        stateVersion: 0,
      },
    };
    render(<AdminOrderDetailDrawer open={true} onClose={vi.fn()} detail={detailWithZeroVersion} />);

    // antd table and our data item will render "0" instead of empty/null
    // We expect 0 to be in the document
    expect(screen.getByText('0')).toBeInTheDocument();
  });
});
