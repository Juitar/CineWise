import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import dayjs from 'dayjs';
import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdminOrderFilters } from './AdminOrderFilters';

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

// 注入 antd Mock
vi.mock('antd', async (importOriginal) => {
  const actual = await importOriginal<typeof import('antd')>();

  interface MockRangePickerProps {
    onChange: (dates: [dayjs.Dayjs, dayjs.Dayjs], dateStrings: [string, string]) => void;
    placeholder?: [string, string];
  }

  const MockRangePicker = ({ onChange, placeholder }: MockRangePickerProps) => (
    <div>
      <input
        placeholder={placeholder?.[0] || '开始日期'}
        data-testid="mock-range-picker-start"
        onChange={() => {
          onChange([dayjs('2026-08-01'), dayjs('2026-08-10')], ['2026-08-01', '2026-08-10']);
        }}
      />
    </div>
  );
  return {
    ...actual,
    DatePicker: {
      ...actual.DatePicker,
      RangePicker: MockRangePicker,
    },
  };
});

describe('AdminOrderFilters', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('正确渲染所有允许的筛选条件，不包含影片名称', () => {
    render(<AdminOrderFilters onSearch={vi.fn()} onReset={vi.fn()} />);

    expect(screen.getByText('订单号')).toBeInTheDocument();
    expect(screen.getByText('用户关键词')).toBeInTheDocument();
    expect(screen.getByText('订单状态')).toBeInTheDocument();
    expect(screen.getByText('影片ID')).toBeInTheDocument();
    expect(screen.getByText('场次ID')).toBeInTheDocument();
    expect(screen.getByText('下单时间')).toBeInTheDocument();

    expect(screen.queryByText('影片名称')).not.toBeInTheDocument();
  });

  it('点击查询时传递合法的白名单查询条件，按要求格式输出', () => {
    const handleSearch = vi.fn();
    render(<AdminOrderFilters onSearch={handleSearch} onReset={vi.fn()} />);

    const orderNoInput = screen.getByPlaceholderText('请输入订单号');
    const userKeywordInput = screen.getByPlaceholderText('请输入用户ID或邮箱关键字');
    const movieIdInput = screen.getByPlaceholderText('请输入影片ID');

    fireEvent.change(orderNoInput, { target: { value: ' CW-123 ' } });
    fireEvent.change(userKeywordInput, { target: { value: 'u1001' } });
    fireEvent.change(movieIdInput, { target: { value: '501' } });

    // 选择状态 (在 antd 中通常可以通过 role='combobox' 找到)
    const comboboxes = screen.getAllByRole('combobox');
    if (comboboxes.length > 0) {
      // 触发 Select 的展开
      fireEvent.mouseDown(comboboxes[0]);
      // 选择 '已支付'
      const option = screen.getByTitle('已支付');
      fireEvent.click(option);
    }
    // 对于 DatePicker，我们在 Mock 的组件上触发 change
    const startDateInput = screen.getByTestId('mock-range-picker-start');
    fireEvent.change(startDateInput, { target: { value: 'trigger' } });

    const searchButtons = screen.getAllByRole('button', { name: /查\s*询/ });
    fireEvent.click(searchButtons[0]);

    expect(handleSearch).toHaveBeenCalledTimes(1);
    expect(handleSearch).toHaveBeenCalledWith(
      expect.objectContaining({
        orderNo: 'CW-123',
        userKeyword: 'u1001',
        movieId: '501',
        status: 'PAID',
        dateFrom: '2026-08-01',
        dateTo: '2026-08-10',
      }),
    );
  });

  it('点击重置时清空输入并调用 onReset 回调', () => {
    const handleReset = vi.fn();
    const handleSearch = vi.fn();
    render(<AdminOrderFilters onSearch={handleSearch} onReset={handleReset} />);

    const orderNoInput = screen.getByPlaceholderText('请输入订单号');
    fireEvent.change(orderNoInput, { target: { value: '1234' } });

    const resetButtons = screen.getAllByRole('button', { name: /重\s*置/ });
    fireEvent.click(resetButtons[0]);

    expect(handleReset).toHaveBeenCalledTimes(1);
    expect(orderNoInput).toHaveValue('');
  });
});
