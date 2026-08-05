import React from 'react';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { OrderConfirmation } from './OrderConfirmation';
import { setMobileView } from '../test-utils';

afterEach(() => cleanup());

const defaultProps = {
  auditoriumName: '1号厅',
  seatLabels: ['5排4座', '5排5座'],
  ticketCount: 2,
  totalAmount: '90.00',
  onSubmit: vi.fn(),
  onRetry: vi.fn(),
  onCancel: vi.fn(),
};

describe('OrderConfirmation 组件', () => {
  it('正常渲染订单确认信息', () => {
    setMobileView(false);
    render(<OrderConfirmation {...defaultProps} />);

    expect(screen.getByText('确认订单信息')).toBeInTheDocument();
    expect(screen.getByText('1号厅')).toBeInTheDocument();
    expect(screen.getByText('5排4座、5排5座')).toBeInTheDocument();
    expect(screen.getByText('2 张')).toBeInTheDocument();
    expect(screen.getByText('90.00')).toBeInTheDocument();

    const submitBtn = screen.getByRole('button', { name: '确认并提交订单' });
    const cancelBtn = screen.getByRole('button', { name: '返回修改' });

    expect(submitBtn).toBeInTheDocument();
    expect(cancelBtn).toBeInTheDocument();
  });

  it('RESULT_UNKNOWN 状态下只显示“重新查询订单结果”按钮', () => {
    setMobileView(false);
    render(<OrderConfirmation {...defaultProps} isResultUnknown={true} />);

    expect(screen.getByText('订单结果未知')).toBeInTheDocument();
    const retryBtn = screen.getByRole('button', { name: '重新查询订单结果' });
    expect(retryBtn).toBeInTheDocument();

    expect(screen.queryByRole('button', { name: '确认并提交订单' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '返回修改' })).not.toBeInTheDocument();
  });

  it('冲突状态显示冲突提示', () => {
    setMobileView(false);
    render(<OrderConfirmation {...defaultProps} isConflict={true} />);

    expect(screen.getByText('所选座位已被他人锁定，请返回重新选座')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '返回修改' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '确认并提交订单' })).not.toBeInTheDocument();
  });

  it('不可售状态显示提示', () => {
    setMobileView(false);
    render(<OrderConfirmation {...defaultProps} isNotAvailable={true} />);

    expect(screen.getByText('所选座位当前不可售，请返回重新选座')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '返回修改' })).toBeInTheDocument();
  });

  it('移动端样式下正常渲染提交和取消按钮', () => {
    setMobileView(true);
    render(<OrderConfirmation {...defaultProps} />);

    expect(screen.getByRole('button', { name: '确认并提交订单' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '返回修改' })).toBeInTheDocument();
  });

  it('Loading 状态显示', () => {
    setMobileView(false);
    const { container } = render(<OrderConfirmation {...defaultProps} loading={true} />);
    expect(container.querySelector('.ant-spin')).toBeInTheDocument();
  });

  it('提交中禁用建单和返回操作，避免重复提交', () => {
    setMobileView(false);
    render(<OrderConfirmation {...defaultProps} submitting />);

    expect(screen.getByRole('button', { name: /确认并提交订单/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: '返回修改' })).toBeDisabled();
  });

  it('查询上下文失败时允许外层执行只读重试', () => {
    setMobileView(false);
    const onErrorAction = vi.fn();
    render(
      <OrderConfirmation
        {...defaultProps}
        error={new Error('场次查询失败')}
        errorActionLabel="重新加载"
        onErrorAction={onErrorAction}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '重新加载' }));

    expect(onErrorAction).toHaveBeenCalledTimes(1);
  });
});
