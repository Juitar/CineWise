import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ElectronicTicketCard } from './ElectronicTicketCard';
import { setupTestEnvironment } from '../test-utils';

setupTestEnvironment();

describe('ElectronicTicketCard 组件', () => {
  it('正确渲染有效电子票信息、取票码与本地二维码', () => {
    render(
      <ElectronicTicketCard
        ticketCode="202608051234"
        orderNo="202608050001"
        showTitle="流浪地球3"
        showTime="2026-08-10T14:30:00+08:00"
        cinemaName="妙语影城（大悦城店）"
        seatLabels={['5排10座', '5排11座']}
        status="VALID"
        qrPayload="cinewise:ticket:202608051234"
      />,
    );
    expect(screen.getByText('流浪地球3')).toBeInTheDocument();
    expect(screen.getByText('202608051234')).toBeInTheDocument();
    expect(screen.getByLabelText('有效电子票二维码')).toBeInTheDocument();
    expect(screen.getByText('有效票可入场')).toBeInTheDocument();
  });

  it('当状态为 REFUNDED 时，显示已退票标记且不可显示为有效票', () => {
    render(
      <ElectronicTicketCard ticketCode="202608051234" orderNo="202608050001" status="REFUNDED" />,
    );
    expect(screen.queryByText('有效票可入场')).not.toBeInTheDocument();
    expect(screen.getByText('已退票 (不可用)')).toBeInTheDocument();
    expect(screen.getByText('已退票')).toBeInTheDocument();
  });
});
