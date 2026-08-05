import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { PaymentResult } from './PaymentResult';
import { setupTestEnvironment } from '../test-utils';

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
});
