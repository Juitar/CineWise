import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { PaymentResult } from './PaymentResult';
import { setupTestEnvironment, setMobileView } from '../test-utils';

setupTestEnvironment();

describe('PaymentResult 组件', () => {
  it('正确渲染支付成功状态和按钮文案', () => {
    const handleViewOrder = vi.fn();
    render(
      <PaymentResult
        orderNo="202608050001"
        amount="78.00"
        status="SUCCESS"
        onViewOrder={handleViewOrder}
      />,
    );
    expect(screen.getByText('支付成功')).toBeInTheDocument();
    expect(screen.getByText('订单号：202608050001')).toBeInTheDocument();

    const viewBtn = screen.getByRole('button', { name: '查看订单' });
    fireEvent.click(viewBtn);
    expect(handleViewOrder).toHaveBeenCalled();
  });

  it('在 RESULT_UNKNOWN 状态下不显示“再次支付”，只提供查询和查看按钮', () => {
    render(<PaymentResult orderNo="202608050001" amount="78.00" status="RESULT_UNKNOWN" />);
    expect(screen.queryByRole('button', { name: '前往支付' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '再次支付' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新查询订单结果' })).toBeInTheDocument();
  });

  it('取消和退款订单分别展示真实终态原因，不伪装为订单过期', () => {
    const { rerender } = render(
      <PaymentResult orderNo="202608050001" amount="78.00" status="CANCELLED" />,
    );
    expect(screen.getByText('订单已取消')).toBeInTheDocument();
    expect(screen.queryByText('订单已过期')).not.toBeInTheDocument();

    rerender(<PaymentResult orderNo="202608050001" amount="78.00" status="REFUNDED" />);
    expect(screen.getByText('订单已退款')).toBeInTheDocument();
    expect(screen.getByText(/关联电子票已失效/)).toBeInTheDocument();
  });

  it('在 INITIALIZED 状态下显示待支付 UI', () => {
    render(<PaymentResult orderNo="202608050001" amount="78.00" status="INITIALIZED" />);
    expect(screen.getByText('订单仍待支付')).toBeInTheDocument();
  });
  it('在移动端渲染 ErrorBlock 和 Button', () => {
    setMobileView(true);
    const { container } = render(<PaymentResult orderNo="1" amount="1" status="RESULT_UNKNOWN" />);
    expect(container.querySelector('.adm-error-block')).toBeInTheDocument();
    expect(container.querySelector('.adm-button')).toBeInTheDocument();
  });

  it('支付成功且有 ticketId 时显示电子票入口', () => {
    const handleViewTicket = vi.fn();
    render(
      <PaymentResult
        orderNo="1"
        amount="1.00"
        status="SUCCESS"
        ticketId="T123"
        onViewTicket={handleViewTicket}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '查看电子票' }));
    expect(handleViewTicket).toHaveBeenCalledTimes(1);
  });

  it('支付成功但缺少 ticketId 或入口回调时不显示电子票入口', () => {
    const { rerender } = render(
      <PaymentResult orderNo="1" amount="1.00" status="SUCCESS" ticketId="T123" />,
    );
    expect(screen.queryByRole('button', { name: '查看电子票' })).not.toBeInTheDocument();

    rerender(<PaymentResult orderNo="1" amount="1.00" status="SUCCESS" />);
    expect(screen.queryByRole('button', { name: '查看电子票' })).not.toBeInTheDocument();
  });
});
