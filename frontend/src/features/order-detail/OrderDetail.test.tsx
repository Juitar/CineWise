import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { OrderDetail } from './OrderDetail';
import { setupTestEnvironment, setMobileView } from '../test-utils';

setupTestEnvironment();

describe('OrderDetail 组件', () => {
  const defaultProps = {
    orderNo: '202608050001',
    status: 'PAID' as const,
    showTitle: '流浪地球3',
    showTime: '2026-08-10T14:30:00+08:00',
    cinemaName: '妙语影城（大悦城店）',
    ticketCount: 2,
    unitPrice: '39.00',
    totalAmount: '78.00',
  };

  it('正确渲染已支付状态的订单详情及对应按钮', () => {
    const handleViewTicket = vi.fn();
    const handleApplyRefund = vi.fn();
    render(
      <OrderDetail
        {...defaultProps}
        onViewTicket={handleViewTicket}
        onApplyRefund={handleApplyRefund}
      />,
    );
    expect(screen.getByText('订单详情')).toBeInTheDocument();
    expect(screen.getByText('流浪地球3')).toBeInTheDocument();
    expect(screen.queryByText(/场次编号[：:]/)).not.toBeInTheDocument();
    expect(screen.queryByText(/座位编号[：:]/)).not.toBeInTheDocument();
    expect(screen.getByText('¥ 78.00')).toBeInTheDocument();

    const ticketBtn = screen.getByRole('button', { name: '查看电子票' });
    const refundBtn = screen.getByRole('button', { name: '申请退票' });
    expect(ticketBtn).toBeInTheDocument();
    expect(refundBtn).toBeInTheDocument();

    fireEvent.click(ticketBtn);
    expect(handleViewTicket).toHaveBeenCalled();

    fireEvent.click(refundBtn);
    expect(handleApplyRefund).toHaveBeenCalled();
  });

  it('待支付状态渲染去支付与取消订单按钮，且只触发回调', () => {
    const handlePay = vi.fn();
    const handleCancel = vi.fn();
    render(
      <OrderDetail
        {...defaultProps}
        status="PENDING_PAYMENT"
        onPay={handlePay}
        onCancel={handleCancel}
      />,
    );
    const payBtn = screen.getByRole('button', { name: '去支付' });
    const cancelBtn = screen.getByRole('button', { name: '取消订单' });
    fireEvent.click(payBtn);
    expect(handlePay).toHaveBeenCalled();
    fireEvent.click(cancelBtn);
    expect(handleCancel).toHaveBeenCalled();
  });

  it('PAYING 状态只允许查询支付结果，不显示取消订单', () => {
    render(<OrderDetail {...defaultProps} status="PAYING" onPay={vi.fn()} />);
    expect(screen.getByText('支付确认中')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '查询支付结果' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '取消订单' })).not.toBeInTheDocument();
  });

  it('取消响应未知时只显示订单状态恢复入口', () => {
    const recover = vi.fn();
    render(
      <OrderDetail
        {...defaultProps}
        status="PENDING_PAYMENT"
        cancelResultUnknown={true}
        onRecoverCancel={recover}
      />,
    );
    expect(screen.queryByRole('button', { name: '取消订单' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重新查询订单状态' }));
    expect(recover).toHaveBeenCalledTimes(1);
  });

  it('终态订单仅提供订单列表入口，不再提供返回首页', () => {
    const onViewOrders = vi.fn();
    render(<OrderDetail {...defaultProps} status="CANCELLED" onViewOrders={onViewOrders} />);

    fireEvent.click(screen.getByRole('button', { name: '查看订单列表' }));
    expect(onViewOrders).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('button', { name: '返回首页' })).not.toBeInTheDocument();
  });

  it('桌面端和移动端加载时使用对应组件库的骨架屏', () => {
    setMobileView(false);
    const desktop = render(<OrderDetail {...defaultProps} loading={true} />);
    expect(screen.getByLabelText('订单详情加载中')).toBeInTheDocument();
    expect(desktop.container.querySelector('.ant-skeleton')).toBeInTheDocument();
    expect(screen.queryByText('正在读取订单详细信息...')).not.toBeInTheDocument();
    desktop.unmount();

    setMobileView(true);
    const { container, rerender } = render(<OrderDetail {...defaultProps} loading={true} />);
    expect(container.querySelector('.adm-skeleton')).toBeInTheDocument();
    expect(container.querySelector('.adm-spin-loading')).not.toBeInTheDocument();

    rerender(<OrderDetail {...defaultProps} error="测试错误" />);
    expect(container.querySelector('.adm-error-block')).toBeInTheDocument();

    rerender(<OrderDetail {...defaultProps} status="PENDING_PAYMENT" />);
    expect(container.querySelectorAll('.adm-button').length).toBeGreaterThan(0);
  });
});
