import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ContentFreshness } from '../../shared/types/api';
import { setupTestEnvironment } from '../test-utils';
import { OrderContentNotice } from './OrderContentNotice';

setupTestEnvironment();

const expiredMovie: Partial<ContentFreshness> = {
  source: 'demo-seed',
  sourceType: 'MOCK',
  dataTime: '2026-08-06T09:00:00+08:00',
  expiresAt: '2026-08-06T10:00:00+08:00',
  isExpired: true,
  degraded: true,
  fallbackType: 'MOCK',
};

describe('OrderContentNotice', () => {
  it('标记过期和演示内容来源，不显示为实时资料', () => {
    render(
      <OrderContentNotice
        isLoading={false}
        hasUnavailableContent={false}
        movieFreshness={[expiredMovie]}
        onRetry={vi.fn()}
      />,
    );

    expect(screen.getByText('影片资料：数据已过期，仅供参考')).toBeInTheDocument();
    expect(screen.getByText('影片资料：当前为降级数据')).toBeInTheDocument();
    expect(screen.getByText('影片资料：演示数据')).toBeInTheDocument();
    expect(screen.queryByText('重试内容信息')).not.toBeInTheDocument();
  });

  it('内容失败时显示明确兜底和重试入口', () => {
    const onRetry = vi.fn();
    render(<OrderContentNotice isLoading={false} hasUnavailableContent onRetry={onRetry} />);

    fireEvent.click(screen.getByText('重试内容信息'));
    expect(screen.getByText('部分影片或影院信息暂不可用')).toBeInTheDocument();
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
