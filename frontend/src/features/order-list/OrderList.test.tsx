import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { OrderList } from './OrderList';
import { setupTestEnvironment } from '../test-utils';

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
});
