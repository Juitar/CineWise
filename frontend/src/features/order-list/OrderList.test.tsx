import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { OrderList } from './OrderList';
import { setupTestEnvironment, setMobileView } from '../test-utils';

setupTestEnvironment();

describe('OrderList 组件', () => {
  const sampleOrders = [
    {
      orderId: '10001',
      orderNo: '202608050001',
      showTitle: '流浪地球3',
      showId: '1001',
      showTime: '2026-08-10T14:30:00+08:00',
      ticketCount: 2,
      totalAmount: '78.00',
      status: 'PAID' as const,
    },
    {
      orderId: '10002',
      orderNo: '202608050002',
      showTitle: '封神第二部',
      showTime: '2026-08-11T19:00:00+08:00',
      ticketCount: 1,
      totalAmount: '45.00',
      status: 'PENDING_PAYMENT' as const,
    },
  ];

  it('正确渲染正常列表、订单卡片及金额文案', () => {
    render(<OrderList orders={sampleOrders} />);
    expect(screen.getByText('我的订单')).toBeInTheDocument();
    expect(screen.getByText('订单号：202608050001')).toBeInTheDocument();
    expect(screen.getByText('流浪地球3')).toBeInTheDocument();
    expect(screen.getByText('场次编号：1001')).toBeInTheDocument();
    expect(screen.getByText('实付款 ¥ 78.00')).toBeInTheDocument();
    expect(screen.getByLabelText('按下单日期筛选订单')).toBeInTheDocument();
    expect(screen.getAllByText('已出票').length).toBeGreaterThanOrEqual(1);
  });

  it('PAYING 状态使用统一的支付确认中文文案', () => {
    render(
      <OrderList
        orders={[
          {
            ...sampleOrders[0],
            orderId: '10003',
            orderNo: '202608050003',
            status: 'PAYING',
          },
        ]}
      />,
    );

    expect(screen.getByText('支付确认中')).toBeInTheDocument();
  });

  it('无数据时显示空状态 Empty 提示', () => {
    render(<OrderList orders={[]} />);
    expect(screen.getByText('暂无符合条件的购票订单')).toBeInTheDocument();
  });

  it('验证唯一按钮可键盘操作且只触发一次 callback', () => {
    const handleClick = vi.fn();
    render(<OrderList orders={sampleOrders} onOrderClick={handleClick} />);
    const btns = screen.getAllByRole('button', { name: '查看详情' });
    expect(btns.length).toBe(2);
    btns[0].focus();
    expect(document.activeElement).toBe(btns[0]);
    fireEvent.click(btns[0]);
    expect(handleClick).toHaveBeenCalledTimes(1);
    expect(handleClick).toHaveBeenCalledWith('202608050001');
  });

  it('在移动端视图渲染订单骨架屏和 ErrorBlock', () => {
    setMobileView(true);
    const { container, rerender } = render(<OrderList orders={[]} loading={true} />);
    expect(container.querySelector('.adm-skeleton')).toBeInTheDocument();

    rerender(<OrderList orders={[]} loading={false} error="测试错误" />);
    expect(container.querySelector('.adm-error-block')).toBeInTheDocument();
  });

  it('在桌面端加载订单时渲染骨架屏', () => {
    setMobileView(false);
    const { container } = render(<OrderList orders={[]} loading={true} />);
    expect(container.querySelectorAll('.order-card-skeleton')).toHaveLength(3);
    expect(container.querySelector('.ant-skeleton')).toBeInTheDocument();
  });
});
