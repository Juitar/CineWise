import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

vi.mock('umi', () => ({
  Navigate: ({ replace, to }: { replace: boolean; to: string }) => (
    <span data-replace={String(replace)} data-to={to}>
      管理端入口
    </span>
  ),
}));

import AdminIndexPage from './index';

describe('AdminIndexPage', () => {
  it('进入正式内容工作台', () => {
    render(<AdminIndexPage />);

    expect(screen.getByText('管理端入口')).toHaveAttribute('data-to', '/admin/content');
    expect(screen.getByText('管理端入口')).toHaveAttribute('data-replace', 'true');
  });
});
