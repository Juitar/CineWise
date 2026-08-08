import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
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

afterEach(() => {
  vi.useRealTimers();
  cleanup();
});

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
    ['thinking', '正在查询场次'],
    ['error', '暂时无法完成'],
  ] as const)('渲染 %s 展示项', (kind, text) => {
    renderItem({ key: kind, kind, text });
    expect(screen.getByText(text)).toBeInTheDocument();
    expect(screen.getByRole('listitem')).toHaveAttribute('data-agent-card-kind', kind);
  });

  it('思考中提示使用助手气泡和点状动画，不展示处理进度卡片', () => {
    renderItem({ key: 'thinking', kind: 'thinking', text: '正在规划观影方案' });
    expect(screen.getByRole('status')).toHaveTextContent('思考中正在规划观影方案');
    expect(screen.queryByText('处理进度')).not.toBeInTheDocument();
    expect(document.querySelectorAll('.agent-thinking-dots i')).toHaveLength(3);
  });

  it('问题卡提交选项值并支持自由文本', () => {
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
    expect(screen.getByRole('textbox', { name: '补充回答' })).toHaveValue('2099-08-05');
    expect(screen.getByText('回答已提交')).toBeInTheDocument();
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
              movieId: '1001',
              cinemaId: '2001',
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

  it('危险 HTML 不会作为页面元素渲染', () => {
    const { container } = renderItem({
      key: 'safe-text',
      kind: 'assistant-text',
      text: '<img src=x onerror=alert(1)>',
    });
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('.agent-card-markdown')).toBeEmptyDOMElement();
  });

  it('安全渲染 Markdown，不允许模型文本注入 HTML', () => {
    const { container } = renderItem({
      key: 'markdown',
      kind: 'assistant-text',
      text: '**推荐理由**\n\n- 时间合适\n- <script>alert(1)</script>',
    });
    expect(screen.getByText('推荐理由').tagName).toBe('STRONG');
    expect(container.querySelector('.agent-card-markdown')).toHaveTextContent('时间合适');
    expect(container.querySelector('script')).toBeNull();
  });

  it('流式内容在新的分片到达后继续追加，不从头闪回', () => {
    vi.useFakeTimers();
    const { rerender } = renderItem({
      key: 'stream',
      kind: 'assistant-text',
      text: '你好',
      typing: true,
      streamId: 'run-1',
    });
    act(() => vi.advanceTimersByTime(70));
    expect(screen.getByRole('list')).toHaveTextContent('你好');

    rerender(
      <div role="list">
        <AgentDisplayItemView
          item={{
            key: 'stream',
            kind: 'assistant-text',
            text: '你好，欢迎使用妙语。',
            typing: true,
            streamId: 'run-1',
          }}
          answerDisabled={false}
          onAnswer={onAnswer}
          onConfirm={onConfirm}
        />
      </div>,
    );
    act(() => vi.advanceTimersByTime(35));
    expect(screen.getByRole('list')).toHaveTextContent('你好，');
    act(() => vi.runAllTimers());
    expect(screen.getByRole('list')).toHaveTextContent('你好，欢迎使用妙语。');
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
