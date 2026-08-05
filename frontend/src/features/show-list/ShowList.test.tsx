import React from 'react';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { ShowList } from './ShowList';
import type { ShowItemUI } from './ShowList';
import { setMobileView } from '../test-utils';

afterEach(() => cleanup());

const mockShows: ShowItemUI[] = [
  {
    showId: 'show-1',
    movieId: 'm-1',
    cinemaId: 'c-1',
    cinemaName: 'Test Cinema',
    auditoriumId: 'a-1',
    auditoriumName: '1号厅',
    startTimeText: '10:00',
    endTimeText: '12:00',
    expiresAt: '2023-01-01T09:45:00Z',
    languageVersion: '原版 2D',
    basePrice: '45.00',
    availableSeatCount: 100,
    status: 'ON_SALE',
    dataType: 'test',
    stateVersion: 1,
    updatedAt: '2023-01-01T00:00:00Z',
  },
  {
    showId: 'show-2',
    movieId: 'm-1',
    cinemaId: 'c-1',
    cinemaName: 'Test Cinema',
    auditoriumId: 'a-2',
    auditoriumName: '2号厅',
    startTimeText: '13:00',
    endTimeText: '15:00',
    expiresAt: '2023-01-01T12:45:00Z',
    languageVersion: '国语 3D',
    basePrice: '60.00',
    availableSeatCount: 0,
    status: 'SOLD_OUT',
    dataType: 'test',
    stateVersion: 1,
    updatedAt: '2023-01-01T00:00:00Z',
  },
];

describe('ShowList 组件', () => {
  it('正确渲染场次列表和信息', () => {
    setMobileView(false);
    render(<ShowList shows={mockShows} />);

    expect(screen.getByText('10:00')).toBeInTheDocument();
    expect(screen.getByText('12:00 散场')).toBeInTheDocument();
    expect(screen.getByText('1号厅')).toBeInTheDocument();
    expect(screen.getByText('原版 2D')).toBeInTheDocument();
    expect(screen.getByText('45.00')).toBeInTheDocument();
    expect(screen.getByText('余 100 座')).toBeInTheDocument();

    const buyButtons = screen.getAllByRole('button', { name: /去选座|已满座/i });
    expect(buyButtons).toHaveLength(2);
    expect(buyButtons[0]).toHaveTextContent('去选座');
    expect(buyButtons[0]).not.toBeDisabled();
    expect(buyButtons[1]).toHaveTextContent('已满座');
    expect(buyButtons[1]).toBeDisabled();
  });

  it('点击“去选座”正确触发回调', () => {
    setMobileView(false);
    const onSelectShow = vi.fn();
    render(<ShowList shows={mockShows} onSelectShow={onSelectShow} />);

    const buyButton = screen.getByRole('button', { name: '去选座' });
    fireEvent.click(buyButton);

    expect(onSelectShow).toHaveBeenCalledTimes(1);
    expect(onSelectShow).toHaveBeenCalledWith('show-1');
  });

  it('Loading 状态显示', () => {
    setMobileView(false);
    const { container } = render(<ShowList shows={[]} loading />);
    expect(container.querySelector('.ant-spin')).toBeInTheDocument();
  });

  it('空状态显示', () => {
    setMobileView(false);
    render(<ShowList shows={[]} />);
    expect(screen.getByText('暂无可售场次')).toBeInTheDocument();
  });

  it('错误状态显示', () => {
    setMobileView(false);
    render(<ShowList shows={[]} error={new Error('网络异常')} />);
    expect(screen.getByText('加载场次失败')).toBeInTheDocument();
    expect(screen.getByText('网络异常')).toBeInTheDocument();
  });

  it('查询失败时通过回调请求重新加载', () => {
    setMobileView(false);
    const onRetry = vi.fn();
    render(<ShowList shows={[]} error={new Error('网络异常')} onRetry={onRetry} />);

    fireEvent.click(screen.getByRole('button', { name: '重新加载' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('移动端下也能正常渲染按钮', () => {
    setMobileView(true);
    render(<ShowList shows={[mockShows[0]]} />);
    const buyButton = screen.getByRole('button', { name: '去选座' });
    expect(buyButton).toBeInTheDocument();
  });
});
