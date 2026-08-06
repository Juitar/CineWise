import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { setupTestEnvironment } from '../test-utils';
import { TransactionBackButton } from './TransactionBackButton';

setupTestEnvironment();

describe('TransactionBackButton', () => {
  it('点击安全返回入口时只调用页面传入的业务导航回调', () => {
    const onBack = vi.fn();
    render(<TransactionBackButton onBack={onBack} label="返回订单列表" />);

    fireEvent.click(screen.getByLabelText('返回订单列表（不重新提交订单）'));

    expect(onBack).toHaveBeenCalledTimes(1);
    expect(screen.getByText('返回订单列表')).toBeInTheDocument();
  });
});
