import { fireEvent, render, screen } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  pathname: '/admin/content',
  push: vi.fn(),
}));

vi.mock('umi', () => ({
  Link: ({ children, className, to }: PropsWithChildren<{ className?: string; to: string }>) => (
    <a className={className} href={to}>
      {children}
    </a>
  ),
  history: { push: mocks.push },
  useLocation: () => ({ pathname: mocks.pathname }),
}));

import { AdminSidebar } from './AdminSidebar';

describe('AdminSidebar', () => {
  beforeEach(() => {
    mocks.pathname = '/admin/content';
    mocks.push.mockReset();
  });

  it('使用已注册的三个正式管理路由', () => {
    render(<AdminSidebar />);

    expect(screen.getByRole('link', { name: /妙语购票/ })).toHaveAttribute(
      'href',
      '/admin/content',
    );
    expect(screen.getByText('工作台')).toBeInTheDocument();
    expect(screen.getByText('订单管理')).toBeInTheDocument();
    expect(screen.getByText('Agent轨迹')).toBeInTheDocument();

    fireEvent.click(screen.getByText('Agent轨迹'));
    expect(mocks.push).toHaveBeenCalledWith('/admin/agent-runs');
  });
});
