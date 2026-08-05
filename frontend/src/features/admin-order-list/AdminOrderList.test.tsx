import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockPageNormal } from '../../pages/admin/orders/mock';
import { AdminOrderList } from './AdminOrderList';

// 注入 matchMedia mock
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(), // Deprecated
    removeListener: vi.fn(), // Deprecated
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

describe('AdminOrderList', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('渲染正常列表，并包含用户信息、影片、场次', () => {
    render(
      <AdminOrderList
        orders={mockPageNormal.records}
        total={mockPageNormal.total}
        page={mockPageNormal.page}
        size={mockPageNormal.size}
        onPageChange={vi.fn()}
        onViewDetail={vi.fn()}
      />,
    );

    // mockSummaryPending
    expect(screen.getAllByText('CW-PENDING-1001').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('a***@example.com').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('待支付').length).toBeGreaterThanOrEqual(1);

    // mockSummaryPaid
    expect(screen.getAllByText('CW-PAID-1002').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('b***@example.com').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('已支付').length).toBeGreaterThanOrEqual(1);
  });

  it('emailMasked 为 null 时显示 "用户信息不可用"', () => {
    render(
      <AdminOrderList
        orders={mockPageNormal.records}
        total={mockPageNormal.total}
        page={mockPageNormal.page}
        size={mockPageNormal.size}
        onPageChange={vi.fn()}
        onViewDetail={vi.fn()}
      />,
    );

    // Table 和 Card 都会渲染所以可能大于 1
    expect(screen.getAllByText('用户信息不可用').length).toBeGreaterThanOrEqual(1);
  });

  it('状态字段为 null 时显示 "未产生"', () => {
    render(
      <AdminOrderList
        orders={mockPageNormal.records}
        total={mockPageNormal.total}
        page={mockPageNormal.page}
        size={mockPageNormal.size}
        onPageChange={vi.fn()}
        onViewDetail={vi.fn()}
      />,
    );

    expect(screen.getAllByText('未产生').length).toBeGreaterThanOrEqual(1);
  });

  it('点击查看详情时调用 onViewDetail', () => {
    const handleViewDetail = vi.fn();
    render(
      <AdminOrderList
        orders={mockPageNormal.records.slice(0, 1)}
        total={1}
        page={1}
        size={20}
        onPageChange={vi.fn()}
        onViewDetail={handleViewDetail}
      />,
    );

    // 因为有 PC 和 移动端，所以会找到 2 个，点击任意一个
    const buttons = screen.getAllByRole('button', { name: /查看详情/ });
    fireEvent.click(buttons[0]);

    expect(handleViewDetail).toHaveBeenCalledTimes(1);
    expect(handleViewDetail).toHaveBeenCalledWith('CW-PENDING-1001');
  });
});
