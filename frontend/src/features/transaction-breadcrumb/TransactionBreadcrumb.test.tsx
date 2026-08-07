import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { TransactionBreadcrumb } from './TransactionBreadcrumb';

vi.mock('umi', () => ({
  Link: ({ to, children }: { to: string; children: React.ReactNode }) => (
    <a href={to}>{children}</a>
  ),
}));

describe('TransactionBreadcrumb', () => {
  it('只将上级业务路径渲染为链接，当前项保持不可点击', () => {
    render(
      <TransactionBreadcrumb
        items={[{ label: '选择场次', to: '/shows?movieId=1&cinemaId=2' }, { label: '选择座位' }]}
      />,
    );

    expect(screen.getByRole('navigation', { name: '面包屑' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '选择场次' })).toHaveAttribute(
      'href',
      '/shows?movieId=1&cinemaId=2',
    );
    expect(screen.getByText('选择座位').closest('a')).toBeNull();
  });
});
