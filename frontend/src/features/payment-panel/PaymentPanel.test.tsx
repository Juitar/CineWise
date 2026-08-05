import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { PaymentPanel } from './PaymentPanel';
import { setupTestEnvironment } from '../test-utils';

setupTestEnvironment();

describe('PaymentPanel 组件', () => {
  it('正确渲染订单信息、金额和本地密码输入区域', () => {
    render(
      <PaymentPanel
        orderNo="202608050001"
        ticketCount={2}
        totalAmount="78.00"
        showTime="2026-08-10 14:30"
      />,
    );
    expect(screen.getByText('202608050001')).toBeInTheDocument();
    expect(screen.getByText('2 张')).toBeInTheDocument();
    expect(screen.getByText('¥ 78.00')).toBeInTheDocument();
    expect(screen.getByText('2026-08-10 14:30')).toBeInTheDocument();
    expect(screen.getByLabelText('六位模拟支付密码')).toBeInTheDocument();
  });

  it('点击确认支付按钮仅抛出 onPay 回调', () => {
    const handlePay = vi.fn();
    render(
      <PaymentPanel orderNo="202608050001" ticketCount={2} totalAmount="78.00" onPay={handlePay} />,
    );
    const passwordInput = screen.getByLabelText('六位模拟支付密码');
    const validLocalInput = ['1', '2', '3', '4', '5', '6'].join('');
    fireEvent.change(passwordInput, { target: { value: validLocalInput } });
    const btn = screen.getByRole('button', { name: '确认支付' });
    expect(btn).not.toBeDisabled();
    fireEvent.click(btn);
    expect(handlePay).toHaveBeenCalledTimes(1);
    expect(passwordInput).toHaveValue('');
  });

  it('在 RESULT_UNKNOWN 状态下不渲染密码占位和支付按钮，仅允许查询订单结果', () => {
    const handleQuery = vi.fn();
    render(
      <PaymentPanel
        orderNo="202608050001"
        ticketCount={2}
        totalAmount="78.00"
        status="RESULT_UNKNOWN"
        onQueryOrderResult={handleQuery}
      />,
    );
    expect(screen.queryByLabelText('六位模拟支付密码')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '确认支付' })).not.toBeInTheDocument();

    const queryBtn = screen.getByRole('button', {
      name: '重新查询订单结果',
    });
    expect(queryBtn).toBeInTheDocument();
    fireEvent.click(queryBtn);
    expect(handleQuery).toHaveBeenCalled();
  });

  it('密码格式不符合六位数字时不触发支付回调', () => {
    const handlePay = vi.fn();
    render(
      <PaymentPanel orderNo="202608050001" ticketCount={2} totalAmount="78.00" onPay={handlePay} />,
    );
    fireEvent.change(screen.getByLabelText('六位模拟支付密码'), {
      target: { value: '12345' },
    });
    fireEvent.click(screen.getByRole('button', { name: '确认支付' }));
    expect(handlePay).not.toHaveBeenCalled();
    expect(screen.getByText('请输入六位数字')).toBeInTheDocument();
  });

  it('处于离线只读模式时，展示只读提示并禁用确认支付按钮', () => {
    render(
      <PaymentPanel
        orderNo="202608050001"
        ticketCount={2}
        totalAmount="78.00"
        isOfflineReadOnly={true}
      />,
    );
    expect(screen.getByText('离线只读提示')).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: '确认支付' });
    expect(btn).toBeDisabled();
  });
});
