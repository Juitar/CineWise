import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { OrderCreateSuccess } from './OrderCreateSuccess';
import { setupTestEnvironment, setMobileView } from '../test-utils';

setupTestEnvironment();

describe('OrderCreateSuccess 组件', () => {
  const defaultProps = {
    orderNo: '202608050002',
    totalAmount: '45.00',
    expireTimeText: '14分59秒',
  };

  it('建单成功组件显示订单号、金额、截止时间', () => {
    render(<OrderCreateSuccess {...defaultProps} />);

    expect(screen.getByText('订单创建成功')).toBeInTheDocument();
    expect(screen.getByText('202608050002')).toBeInTheDocument();
    expect(screen.getByText('45.00')).toBeInTheDocument();
    expect(screen.getByText('14分59秒')).toBeInTheDocument();
  });

  it('通过回调上报去支付与查看订单操作', () => {
    const handlePay = vi.fn();
    const handleViewOrder = vi.fn();
    render(
      <OrderCreateSuccess {...defaultProps} onPay={handlePay} onViewOrder={handleViewOrder} />,
    );

    fireEvent.click(screen.getByRole('button', { name: '去支付' }));
    fireEvent.click(screen.getByRole('button', { name: '查看订单' }));

    expect(handlePay).toHaveBeenCalledTimes(1);
    expect(handleViewOrder).toHaveBeenCalledTimes(1);
  });

  it('没有回调或处于加载状态时按钮不可用', () => {
    const { rerender } = render(<OrderCreateSuccess {...defaultProps} />);

    const loadingPayButton = screen.getByText('去支付').closest('button');
    const loadingViewOrderButton = screen.getByText('查看订单').closest('button');

    expect(loadingPayButton).toBeDisabled();
    expect(loadingViewOrderButton).toBeDisabled();

    rerender(
      <OrderCreateSuccess {...defaultProps} onPay={vi.fn()} onViewOrder={vi.fn()} loading />,
    );

    const submittingPayButton = screen.getByText('去支付').closest('button');
    const submittingViewOrderButton = screen.getByText('查看订单').closest('button');

    expect(submittingPayButton).toBeDisabled();
    expect(submittingViewOrderButton).toBeDisabled();
  });

  it('移动端也提供两个交易出口', () => {
    setMobileView(true);
    render(<OrderCreateSuccess {...defaultProps} onPay={vi.fn()} onViewOrder={vi.fn()} />);

    expect(screen.getByRole('button', { name: '去支付' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '查看订单' })).toBeInTheDocument();
  });
});
