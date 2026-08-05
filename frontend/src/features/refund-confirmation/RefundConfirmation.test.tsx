import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { RefundConfirmation } from './RefundConfirmation';
import { setupTestEnvironment, setMobileView } from '../test-utils';

setupTestEnvironment();

describe('RefundConfirmation 组件', () => {
  const defaultProps = {
    orderNo: '202608050001',
    refundAmount: '78.00',
    orderStatus: 'PAID' as const,
    ticketStatus: 'VALID' as const,
    showStartTime: '2026-08-10T14:30:00+08:00',
    impactText: '退票申请通过后座次将被释放。',
  };

  it('正常状态下选填原因且勾选二次确认后即可提交退票', () => {
    const handleConfirm = vi.fn();
    render(<RefundConfirmation {...defaultProps} onConfirmRefund={handleConfirm} />);
    const btn = screen.getByRole('button', { name: '确认申请退票' });
    expect(btn).toBeDisabled();

    const checkbox = screen.getByRole('checkbox');
    fireEvent.click(checkbox);
    expect(btn).not.toBeDisabled();

    fireEvent.click(btn);
    expect(handleConfirm).toHaveBeenCalledWith(undefined);
  });

  it('订单和电子票状态使用统一中文映射，不展示后端枚举值', () => {
    render(<RefundConfirmation {...defaultProps} />);

    expect(screen.getByText('已出票')).toBeInTheDocument();
    expect(screen.getByText('有效')).toBeInTheDocument();
    expect(screen.queryByText('PAID')).not.toBeInTheDocument();
    expect(screen.queryByText('VALID')).not.toBeInTheDocument();
  });

  it('在 RESULT_UNKNOWN 状态下只提供查询结果按钮，无再次退票或返回按钮', () => {
    const handleQuery = vi.fn();
    render(
      <RefundConfirmation
        {...defaultProps}
        status="RESULT_UNKNOWN"
        onQueryRefundResult={handleQuery}
      />,
    );
    expect(screen.queryByRole('button', { name: '再次退票' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '确认申请退票' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '返回' })).not.toBeInTheDocument();

    const queryBtn = screen.getByRole('button', {
      name: '重新查询退款结果',
    });
    expect(queryBtn).toBeInTheDocument();
    fireEvent.click(queryBtn);
    expect(handleQuery).toHaveBeenCalled();
  });

  it('在 REQUESTED 状态下显示申请已提交提示', () => {
    render(<RefundConfirmation {...defaultProps} status="REQUESTED" />);
    expect(screen.getByText('退款已申请...')).toBeInTheDocument();
  });

  it('在 SUCCESS 状态下使用统一退款成功文案，避免重复前缀', () => {
    render(<RefundConfirmation {...defaultProps} status="SUCCESS" />);

    expect(screen.getByText('退款成功')).toBeInTheDocument();
    expect(screen.queryByText('退款退款成功')).not.toBeInTheDocument();
  });

  it('在移动端 REQUESTED 状态下渲染 SpinLoading', () => {
    setMobileView(true);
    const { container } = render(<RefundConfirmation {...defaultProps} status="REQUESTED" />);
    expect(container.querySelector('.adm-spin-loading')).toBeInTheDocument();
  });
});
