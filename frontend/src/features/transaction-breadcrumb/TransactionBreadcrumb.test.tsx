import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import {
  ORDERS_BREADCRUMB_ITEM,
  PROFILE_BREADCRUMB_ITEM,
  TransactionBreadcrumb,
} from './TransactionBreadcrumb';

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

  it('订单后续页面共用个人中心和我的订单根层级', () => {
    render(
      <TransactionBreadcrumb
        items={[PROFILE_BREADCRUMB_ITEM, ORDERS_BREADCRUMB_ITEM, { label: '订单详情' }]}
      />,
    );

    expect(screen.getByRole('link', { name: '个人中心' })).toHaveAttribute('href', '/profile');
    expect(screen.getByRole('link', { name: '我的订单' })).toHaveAttribute('href', '/orders');
    expect(screen.getByText('订单详情').closest('a')).toBeNull();
  });
});
