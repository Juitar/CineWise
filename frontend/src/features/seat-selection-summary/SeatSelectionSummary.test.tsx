import React from 'react';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { SeatSelectionSummary } from './SeatSelectionSummary';
import { setMobileView } from '../test-utils';

afterEach(() => cleanup());

describe('SeatSelectionSummary 组件', () => {
  it('未选择座位时显示占位符且按钮禁用', () => {
    setMobileView(false);
    render(
      <SeatSelectionSummary
        selectedSeats={[]}
        selectedCount={0}
        maxSelectCount={6}
        onConfirm={vi.fn()}
      />,
    );
    expect(screen.getByText('请在上方座位图选择座位')).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: '确认选座' });
    expect(btn).toBeDisabled();
  });

  it('选择座位后正常渲染座位号且按钮可用', () => {
    setMobileView(false);
    const onConfirm = vi.fn();
    render(
      <SeatSelectionSummary
        selectedSeats={[
          { id: '1', label: '5排4座' },
          { id: '2', label: '5排5座' },
        ]}
        selectedCount={2}
        maxSelectCount={6}
        onConfirm={onConfirm}
      />,
    );
    expect(screen.getByText('5排4座')).toBeInTheDocument();
    expect(screen.getByText('5排5座')).toBeInTheDocument();
    expect(screen.getByText('(2/6)')).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: '确认选座' });
    expect(btn).not.toBeDisabled();

    fireEvent.click(btn);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('达到最大限制时显示提示', () => {
    setMobileView(false);
    render(
      <SeatSelectionSummary
        selectedSeats={[
          { id: '1', label: '1' },
          { id: '2', label: '2' },
          { id: '3', label: '3' },
          { id: '4', label: '4' },
          { id: '5', label: '5' },
          { id: '6', label: '6' },
        ]}
        selectedCount={6}
        maxSelectCount={6}
        onConfirm={vi.fn()}
      />,
    );
    expect(screen.getByText('最多只能选择 6 个座位')).toBeInTheDocument();
  });

  it('传入 disabled=true 时按钮强制禁用', () => {
    setMobileView(false);
    render(
      <SeatSelectionSummary
        selectedSeats={[{ id: '1', label: '5排4座' }]}
        selectedCount={1}
        maxSelectCount={6}
        disabled={true}
        onConfirm={vi.fn()}
      />,
    );
    const btn = screen.getByRole('button', { name: '确认选座' });
    expect(btn).toBeDisabled();
  });

  it('移动端正常渲染', () => {
    setMobileView(true);
    render(
      <SeatSelectionSummary
        selectedSeats={[{ id: '1', label: '5排4座' }]}
        selectedCount={1}
        maxSelectCount={6}
        onConfirm={vi.fn()}
      />,
    );
    const btn = screen.getByRole('button', { name: '确认选座' });
    expect(btn).toBeInTheDocument();
  });
});
