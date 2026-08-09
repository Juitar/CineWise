import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

const layoutMocks = vi.hoisted(() => ({
  location: { pathname: '/', search: '' },
  navigate: vi.fn(),
}));

vi.mock('umi', () => ({
  useLocation: () => layoutMocks.location,
  useNavigate: () => layoutMocks.navigate,
}));

vi.mock('antd-mobile', () => ({
  Dropdown: Object.assign(({ children }: React.PropsWithChildren) => <div>{children}</div>, {
    Item: ({ children, title }: React.PropsWithChildren<{ title: string }>) => (
      <section aria-label={`城市选择：${title}`}>{children}</section>
    ),
  }),
  NavBar: ({ children }: React.PropsWithChildren) => <header>{children}</header>,
  Selector: ({
    onChange,
    options,
  }: {
    onChange: (value: Array<'430100' | '330100'>) => void;
    options: Array<{ label: string; value: '430100' | '330100' }>;
  }) => (
    <div>
      {options.map((option) => (
        <button key={option.value} type="button" onClick={() => onChange([option.value])}>
          {option.label}
        </button>
      ))}
    </div>
  ),
}));

import { MobileUserHeader } from './MobileUserHeader';

describe('MobileUserHeader', () => {
  afterEach(() => cleanup());

  it('首页提供受控城市选择，并导航到杭州内容浏览', () => {
    layoutMocks.location = { pathname: '/', search: '' };
    layoutMocks.navigate.mockReset();
    render(<MobileUserHeader />);

    expect(screen.getByLabelText('城市选择：长沙')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '杭州' }));
    expect(layoutMocks.navigate).toHaveBeenCalledWith('/?location=330100');
  });

  it('订单等业务页不显示城市选择，避免改写已有交易上下文', () => {
    layoutMocks.location = { pathname: '/orders/10001', search: '' };
    render(<MobileUserHeader />);

    expect(screen.queryByText('杭州')).not.toBeInTheDocument();
    expect(screen.getByText('妙语购票')).toBeInTheDocument();
  });
});
