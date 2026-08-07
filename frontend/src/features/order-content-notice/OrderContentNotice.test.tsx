import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { setupTestEnvironment } from '../test-utils';
import { OrderContentNotice } from './OrderContentNotice';

setupTestEnvironment();

describe('OrderContentNotice', () => {
  it('内容可用时不在交易页展示来源和时效标签', () => {
    render(
      <OrderContentNotice isLoading={false} hasUnavailableContent={false} onRetry={vi.fn()} />,
    );

    expect(screen.queryByText('内容资料来源与时效')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('内容资料来源和时效')).not.toBeInTheDocument();
    expect(screen.queryByText('重试内容信息')).not.toBeInTheDocument();
  });

  it('内容失败时显示明确兜底和重试入口', () => {
    const onRetry = vi.fn();
    render(<OrderContentNotice isLoading={false} hasUnavailableContent onRetry={onRetry} />);

    fireEvent.click(screen.getByText('重试内容信息'));
    expect(screen.getByText('部分影片或影院信息暂不可用')).toBeInTheDocument();
    expect(screen.getByText('订单、金额、场次和座位信息不受影响。')).toBeInTheDocument();
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
