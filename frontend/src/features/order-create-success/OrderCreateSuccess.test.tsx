import React from 'react';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { OrderCreateSuccess } from './OrderCreateSuccess';
import { setMobileView } from '../test-utils';

afterEach(() => cleanup());

const defaultProps = {
  orderNo: '202608050002',
  totalAmount: '45.00',
  expireTimeText: '14分59秒',
};

describe('OrderCreateSuccess 组件', () => {
  it('建单成功组件显示订单号、金额、截止时间', () => {
    setMobileView(false);
    render(<OrderCreateSuccess {...defaultProps} onPay={vi.fn()} onViewOrder={vi.fn()} />);

    expect(screen.getByText('订单创建成功')).toBeInTheDocument();
    expect(screen.getByText('202608050002')).toBeInTheDocument();
    expect(screen.getByText('45.00')).toBeInTheDocument();
    expect(screen.getByText('14分59秒')).toBeInTheDocument();

    const payBtn = screen.getByRole('button', { name: /去支付/i });
    const viewBtn = screen.getByRole('button', { name: /查看订单/i });

    expect(payBtn).toBeInTheDocument();
    expect(viewBtn).toBeInTheDocument();
  });

  it('点击“去支付”触发 onPay', () => {
    setMobileView(false);
    const onPay = vi.fn();
    render(<OrderCreateSuccess {...defaultProps} onPay={onPay} onViewOrder={vi.fn()} />);

    const payBtn = screen.getByRole('button', { name: /去支付/i });
    fireEvent.click(payBtn);

    expect(onPay).toHaveBeenCalledTimes(1);
  });

  it('点击“查看订单”触发 onViewOrder', () => {
    setMobileView(false);
    const onViewOrder = vi.fn();
    render(<OrderCreateSuccess {...defaultProps} onPay={vi.fn()} onViewOrder={onViewOrder} />);

    const viewBtn = screen.getByRole('button', { name: /查看订单/i });
    fireEvent.click(viewBtn);

    expect(onViewOrder).toHaveBeenCalledTimes(1);
  });

  it('Loading 时按钮禁用', () => {
    setMobileView(false);
    render(
      <OrderCreateSuccess {...defaultProps} loading={true} onPay={vi.fn()} onViewOrder={vi.fn()} />,
    );

    const payBtn = screen.getByRole('button', { name: /去支付/i });
    const viewBtn = screen.getByRole('button', { name: /查看订单/i });

    expect(payBtn).toBeDisabled();
    expect(viewBtn).toBeDisabled();
  });

  it('未提供回调时按钮应禁用', () => {
    setMobileView(false);
    render(<OrderCreateSuccess {...defaultProps} />);

    const payBtn = screen.getByRole('button', { name: /去支付/i });
    const viewBtn = screen.getByRole('button', { name: /查看订单/i });

    expect(payBtn).toBeDisabled();
    expect(viewBtn).toBeDisabled();
  });

  it('移动端下也能正常渲染按钮', () => {
    setMobileView(true);
    render(<OrderCreateSuccess {...defaultProps} onPay={vi.fn()} onViewOrder={vi.fn()} />);

    expect(screen.getByRole('button', { name: /去支付/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /查看订单/i })).toBeInTheDocument();
  });
});
