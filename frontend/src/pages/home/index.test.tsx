import { render, screen } from '@testing-library/react';
import React from 'react';
import { describe, expect, it, vi } from 'vitest';

vi.mock('umi', () => ({
  Link: ({ children, to, ...props }: React.PropsWithChildren<{ to: string }>) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

import HomePage from './index';

describe('HomePage', () => {
  it('提供唯一的页面主标题', () => {
    render(<HomePage />);

    expect(screen.getByRole('heading', { level: 1, name: '妙语购票' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '正在热映' })).toBeInTheDocument();
  });
});
