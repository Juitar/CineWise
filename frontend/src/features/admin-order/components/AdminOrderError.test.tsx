import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdminOrderError } from './AdminOrderError';

describe('AdminOrderError', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders FORBIDDEN state correctly', () => {
    render(<AdminOrderError state="FORBIDDEN" traceId="t123" onRetry={vi.fn()} />);
    expect(screen.getByText('无权限访问')).toBeInTheDocument();
    expect(screen.getByText('当前用户无管理权限，请联系超级管理员。')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /重试/ })).not.toBeInTheDocument();
  });

  it('renders DIRECTORY_UNAVAILABLE state correctly', () => {
    render(<AdminOrderError state="DIRECTORY_UNAVAILABLE" traceId="t456" onRetry={vi.fn()} />);
    expect(screen.getByText('用户信息查询暂不可用')).toBeInTheDocument();
    expect(screen.getByText('当前暂时无法检索用户信息，请稍后重试。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /重试/ })).toBeInTheDocument();
  });

  it('renders GENERAL_ERROR state correctly', () => {
    render(<AdminOrderError state="GENERAL_ERROR" traceId="t789" onRetry={vi.fn()} />);
    expect(screen.getByText('系统错误')).toBeInTheDocument();
    expect(screen.getByText('获取订单列表失败，请稍后重试。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /重试/ })).toBeInTheDocument();
  });

  it('calls onRetry when retry button is clicked', () => {
    const handleRetry = vi.fn();
    render(<AdminOrderError state="GENERAL_ERROR" onRetry={handleRetry} />);
    const retryButton = screen.getByRole('button', { name: /重试/ });
    fireEvent.click(retryButton);
    expect(handleRetry).toHaveBeenCalledTimes(1);
  });

  it('renders QUERY_TOO_BROAD state correctly and hides retry button', () => {
    render(<AdminOrderError state="QUERY_TOO_BROAD" traceId="t999" onRetry={vi.fn()} />);
    expect(screen.getByText('用户查询条件过宽')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /重试/ })).not.toBeInTheDocument();
  });
});
