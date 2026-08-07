import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { AgentDisplayItem } from '../../../modules/agent/projection';
import { AgentDisplayItemView } from './index';

vi.mock('umi', () => ({
  Link: ({
    children,
    to,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to: string }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

afterEach(() => cleanup());

const onConfirm = vi.fn();
const onAnswer = vi.fn(async () => true);

function renderItem(item: AgentDisplayItem) {
  return render(
    <div role="list">
      <AgentDisplayItemView
        item={item}
        answerDisabled={false}
        onAnswer={onAnswer}
        onConfirm={onConfirm}
      />
    </div>,
  );
}

describe('Agent 类型化卡片', () => {
  it.each([
    ['assistant-text', '普通回复'],
    ['user-text', '用户消息'],
    ['completed', '运行已完成'],
    ['progress', '正在查询场次'],
    ['error', '暂时无法完成'],
  ] as const)('渲染 %s 展示项', (kind, text) => {
    renderItem({ key: kind, kind, text });
    expect(screen.getByText(text)).toBeInTheDocument();
    expect(screen.getByRole('listitem')).toHaveAttribute('data-agent-card-kind', kind);
  });

  it('问题卡提交选项值并支持自由文本', async () => {
    renderItem({
      key: 'question',
      kind: 'question',
      title: '想在哪天观看？',
      text: '想在哪天观看？',
      question: {
        questionId: 'question-date-1',
        options: [
          { optionId: 'today', label: '今天', value: '2099-08-05' },
          { optionId: 'tomorrow', label: '明天', value: '2099-08-06' },
        ],
        allowFreeText: true,
        expiresAt: '2099-08-05T10:10:00+08:00',
      },
    });
    fireEvent.click(screen.getByRole('button', { name: /今\s*天/ }));
    expect(onAnswer).toHaveBeenCalledWith('question', '2099-08-05');
    expect(screen.getByRole('textbox', { name: '补充回答' })).toBeInTheDocument();
  });

  it('过期问题卡禁止提交', () => {
    renderItem({
      key: 'expired-question',
      kind: 'question',
      title: '想在哪天观看？',
      text: '想在哪天观看？',
      question: {
        questionId: 'question-date-1',
        options: [{ optionId: 'today', label: '今天', value: '2026-08-05' }],
        allowFreeText: false,
        expiresAt: '2026-08-05T10:10:00+08:00',
      },
    });
    expect(screen.getByRole('button', { name: /今\s*天/ })).toBeDisabled();
    expect(screen.getByText('该问题已过期，请重新发起需求')).toBeInTheDocument();
  });

  it.each([
    ['movie-card', '影片推荐'],
    ['plan-card', '观影方案'],
  ] as const)('%s 展示候选和数据状态', (kind, badge) => {
    const plans =
      kind === 'plan-card'
        ? [
            {
              showId: '3001',
              planType: 'COMPREHENSIVE',
              movieName: '示例影片',
              cinemaName: '示例影院',
              price: '68.00',
              currency: 'CNY',
              startTime: '2099-08-05T11:00:00Z',
              rating: '8.5',
              score: 90,
              reasons: ['匹配条件'],
              source: 'recommendation',
              dataAt: '2099-08-05T10:00:00Z',
              expiresAt: '2099-08-05T10:05:00Z',
              expired: false,
              purchaseEligible: true,
              distanceMeters: 1200,
            },
          ]
        : undefined;
    renderItem({
      key: kind,
      kind,
      title: '推荐结果',
      text: '以下内容来自服务端卡片数据',
      plans,
      fields: [
        ...(kind === 'movie-card' ? [{ label: '片名', value: '示例影片' }] : []),
        { label: '状态', value: '有效' },
      ],
    });
    expect(screen.getByText(badge)).toBeInTheDocument();
    expect(screen.getByText('示例影片')).toBeInTheDocument();
    expect(screen.getByText('有效')).toBeInTheDocument();
  });

  it('业务意图只渲染投影提供的选座路径', () => {
    renderItem({
      key: 'intent',
      kind: 'business-intent',
      title: '已确认场次',
      text: '已确认场次，可选座',
      selectSeatsPath: '/shows/3001/seats?movieId=1001&cinemaId=2001',
    });
    expect(screen.getByRole('link', { name: '去选座' })).toHaveAttribute(
      'href',
      '/shows/3001/seats?movieId=1001&cinemaId=2001',
    );
  });

  it('出行建议卡只使用投影提供的任务 ID 跳转详情页', () => {
    renderItem({
      key: 'travel-advice',
      kind: 'travel-advice-card',
      title: '出行建议',
      text: '以下为当前出行建议',
      travelTaskId: '90001',
    });
    expect(screen.getByRole('link', { name: '查看出行建议详情' })).toHaveAttribute(
      'href',
      '/travel/90001',
    );
  });

  it('确认卡调用外层回调且提交中禁止重复操作', () => {
    const item: AgentDisplayItem = {
      key: 'confirmation',
      kind: 'plan-card',
      title: '确认建单',
      text: '等待你的确认',
      confirmation: {
        actionId: 'action-hidden',
        runId: 'run-hidden',
        status: 'PENDING_CONFIRMATION',
        submitting: false,
      },
    };
    const { rerender } = renderItem(item);
    fireEvent.click(screen.getByRole('button', { name: '确认操作' }));
    expect(onConfirm).toHaveBeenCalledWith('confirmation', true);
    expect(screen.queryByText('action-hidden')).not.toBeInTheDocument();
    expect(screen.getByText('操作确认')).toBeInTheDocument();
    rerender(
      <div role="list">
        <AgentDisplayItemView
          item={{ ...item, confirmation: { ...item.confirmation!, submitting: true } }}
          answerDisabled={false}
          onAnswer={onAnswer}
          onConfirm={onConfirm}
        />
      </div>,
    );
    expect(screen.getByRole('button', { name: /确认操作/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: '拒绝操作' })).toBeDisabled();
  });

  it('危险文本按普通文本渲染', () => {
    const { container } = renderItem({
      key: 'safe-text',
      kind: 'assistant-text',
      text: '<img src=x onerror=alert(1)>',
    });
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });

  it('未知 kind 固定降级且不显示未知内容', () => {
    renderItem({
      key: 'future',
      kind: 'future-card' as AgentDisplayItem['kind'],
      text: '内部工具参数',
    });
    expect(screen.getAllByText('卡片暂不可用')).toHaveLength(2);
    expect(screen.queryByText('内部工具参数')).not.toBeInTheDocument();
  });
});
