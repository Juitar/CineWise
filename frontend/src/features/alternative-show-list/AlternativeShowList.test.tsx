import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { AlternativeShowList } from './AlternativeShowList';
import { setupTestEnvironment, setMobileView } from '../test-utils';

setupTestEnvironment();

describe('AlternativeShowList 组件', () => {
  const sampleShows = [
    {
      showId: '1001',
      movieId: '2001',
      cinemaId: '3001',
      startTime: '2026-08-10T18:00:00+08:00',
      basePrice: '39.00',
      status: 'ON_SALE',
      availableSeatCount: 80,
    },
    {
      showId: '1002',
      movieId: '2001',
      cinemaId: '3001',
      startTime: '2026-08-10T20:30:00+08:00',
      basePrice: '45.00',
      status: 'ON_SALE',
      availableSeatCount: 42,
    },
  ];

  it('正确渲染替补场次卡片列表和票价及余座', () => {
    render(<AlternativeShowList shows={sampleShows} />);
    expect(screen.getByText('推荐替代场次（同影院）')).toBeInTheDocument();
    expect(screen.getByText('2026-08-10T18:00:00+08:00')).toBeInTheDocument();
    expect(screen.getByText('¥ 39.00')).toBeInTheDocument();
    expect(screen.getByText('余座：80 个')).toBeInTheDocument();
  });

  it('无场次数据时展示 Empty 空状态', () => {
    render(<AlternativeShowList shows={[]} />);
    expect(screen.getByText('当前暂无其他可替代的同类放映场次')).toBeInTheDocument();
  });

  it('验证唯一选择此场按钮可键盘操作且只触发一次 callback', () => {
    const handleSelect = vi.fn();
    render(<AlternativeShowList shows={sampleShows} onSelectShow={handleSelect} />);
    const btns = screen.getAllByRole('button', { name: '选择此场' });
    expect(btns.length).toBe(2);
    btns[0].focus();
    expect(document.activeElement).toBe(btns[0]);
    fireEvent.click(btns[0]);
    expect(handleSelect).toHaveBeenCalledTimes(1);
    expect(handleSelect).toHaveBeenCalledWith(sampleShows[0]);
  });

  it('在移动端视图渲染 ErrorBlock 和 SpinLoading 以及 antd-mobile 按钮', () => {
    setMobileView(true);
    const { container, rerender } = render(<AlternativeShowList shows={[]} loading={true} />);
    expect(container.querySelector('.adm-spin-loading')).toBeInTheDocument();

    rerender(<AlternativeShowList shows={[]} error="测试错误" />);
    expect(container.querySelector('.adm-error-block')).toBeInTheDocument();

    rerender(<AlternativeShowList shows={sampleShows} />);
    expect(container.querySelectorAll('.adm-button').length).toBeGreaterThan(0);
  });
});
