import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { AgentDisplayItem } from '../../modules/agent/projection';
import type { AgentWorkspaceStatus } from '../../modules/agent/types';

const mocks = vi.hoisted(() => ({
  isMobile: false,
  navigate: vi.fn(),
  workspace: {
    cancel: vi.fn(),
    confirm: vi.fn(),
    clearAll: vi.fn(),
    clearCurrent: vi.fn(),
    createNewSession: vi.fn(),
    feedback: null as string | null,
    loadStatus: 'ready' as 'error' | 'loading' | 'ready',
    projection: {
      sessionId: 'session-example-1',
      runId: null as string | null,
      planVersion: null,
      lastEventId: '0',
      status: 'IDLE' as AgentWorkspaceStatus,
      safeError: null as string | null,
      items: [] as AgentDisplayItem[],
    },
    refreshSessions: vi.fn(),
    sessions: [] as Array<{
      sessionId: string;
      summary: string | null;
      status: string;
      createdAt: string;
      updatedAt: string;
    }>,
    stopActiveStream: vi.fn(),
    submit: vi.fn(),
    submitQuestionAnswer: vi.fn(),
  },
}));

vi.mock('umi', () => ({
  Link: ({ children, to }: React.PropsWithChildren<{ to: string }>) => <a href={to}>{children}</a>,
  useNavigate: () => mocks.navigate,
}));
vi.mock('../../shared/hooks/useMediaQuery', () => ({ useMediaQuery: () => mocks.isMobile }));
vi.mock('../../modules/agent/useAgentWorkspace', () => ({
  useAgentWorkspace: () => mocks.workspace,
}));

import { AgentWorkspace } from './index';
import { setPendingAgentDraft } from '../../modules/agent/entryDraft';

beforeEach(() => {
  mocks.isMobile = false;
  mocks.workspace.feedback = null;
  mocks.workspace.loadStatus = 'ready';
  mocks.workspace.sessions = [];
  mocks.workspace.projection = {
    sessionId: 'session-example-1',
    runId: null,
    planVersion: null,
    lastEventId: '0',
    status: 'IDLE',
    safeError: null,
    items: [],
  };
  vi.clearAllMocks();
  mocks.workspace.submit.mockResolvedValue(true);
  mocks.workspace.submitQuestionAnswer.mockResolvedValue(true);
});

afterEach(() => {
  cleanup();
});

describe('AgentWorkspace 页面', () => {
  it('覆盖加载和空会话状态', () => {
    mocks.workspace.loadStatus = 'loading';
    const { rerender } = render(<AgentWorkspace sessionId="session-example-1" />);
    expect(screen.getByText('正在加载会话')).toBeInTheDocument();
    mocks.workspace.loadStatus = 'ready';
    rerender(<AgentWorkspace sessionId="session-example-1" />);
    expect(screen.getByText('说说你想看什么电影')).toBeInTheDocument();
    expect(screen.getByText('暂无历史会话')).toBeInTheDocument();
  });

  it('未知卡片安全降级且危险 HTML 不会渲染', () => {
    mocks.workspace.projection.items = [
      { key: '1', kind: 'card-placeholder', text: '推荐卡片数据暂不完整' },
      { key: '2', kind: 'assistant-text', text: '<img src=x onerror=alert(1)>' },
    ];
    const { container } = render(<AgentWorkspace sessionId="session-example-1" />);
    expect(screen.getByText('推荐卡片数据暂不完整')).toBeInTheDocument();
    expect(screen.queryByText('<img src=x onerror=alert(1)>')).not.toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
    expect(screen.queryByText(/¥|库存|路线|餐饮/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /购票|确认|支付/ })).not.toBeInTheDocument();
  });

  it('类型化卡片展示服务端白名单字段，不生成确认、购票或支付按钮', () => {
    mocks.workspace.projection.items = [
      {
        key: 'plan-1',
        kind: 'plan-card',
        title: '推荐方案',
        text: '以下内容来自服务端卡片数据',
        fields: [
          { label: '方案 1 · 影片 ID', value: '1001' },
          { label: '方案 1 · 影院 ID', value: '2001' },
          { label: '方案 1 · 场次 ID', value: '3001' },
          { label: '来源', value: 'recommendation' },
          { label: '状态', value: '已过期' },
        ],
      },
    ];
    render(<AgentWorkspace sessionId="session-example-1" />);
    expect(screen.getByText('推荐方案')).toBeInTheDocument();
    expect(screen.getByText('3001')).toBeInTheDocument();
    expect(screen.getByText('已过期')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /购票|确认|拒绝|支付/ })).not.toBeInTheDocument();
  });

  it('问题卡回答走专用提交入口，不复用主输入框入口', async () => {
    mocks.workspace.projection.items = [
      {
        key: 'question-1',
        kind: 'question',
        title: '想在哪天观看？',
        text: '想在哪天观看？',
        question: {
          questionId: 'question-date-1',
          options: [{ optionId: 'tomorrow', label: '明天', value: '明天' }],
          allowFreeText: true,
          expiresAt: '2099-08-08T10:00:00+08:00',
        },
      },
    ];

    render(<AgentWorkspace sessionId="session-example-1" />);
    fireEvent.click(screen.getByRole('button', { name: /明\s*天/ }));

    await vi.waitFor(() => {
      expect(mocks.workspace.submitQuestionAnswer).toHaveBeenCalledWith('明天');
    });
    expect(mocks.workspace.submit).not.toHaveBeenCalled();
  });

  it('只为已校验的选座意图显示去选座入口', () => {
    mocks.workspace.projection.items = [
      {
        key: 'select-seats-1',
        kind: 'business-intent',
        text: '已确认场次，可选座',
        selectSeatsPath: '/shows/70001/seats?movieId=10001&cinemaId=20001',
      },
      {
        key: 'plan-1',
        kind: 'plan-card',
        text: '推荐方案',
      },
      {
        key: 'unsupported-1',
        kind: 'card-placeholder',
        text: '暂不支持此类 Agent 内容',
      },
    ];

    render(<AgentWorkspace sessionId="session-example-1" />);

    expect(screen.getByRole('link', { name: '去选座' })).toHaveAttribute(
      'href',
      '/shows/70001/seats?movieId=10001&cinemaId=20001',
    );
    expect(screen.queryByRole('button', { name: /确认|支付|建单|锁座/ })).not.toBeInTheDocument();
  });

  it('桌面与移动视图共用当前状态，移动端提供会话抽屉', () => {
    mocks.workspace.sessions = [
      {
        sessionId: 'session-example-1',
        summary: '当前会话',
        status: 'ACTIVE',
        createdAt: '2026-08-05T10:00:00+08:00',
        updatedAt: '2026-08-05T10:00:00+08:00',
      },
    ];
    const { rerender } = render(<AgentWorkspace sessionId="session-example-1" />);
    expect(screen.getByText('当前会话')).toBeInTheDocument();
    fireEvent.click(screen.getByText('当前会话'));
    expect(mocks.workspace.stopActiveStream).toHaveBeenCalledOnce();
    expect(mocks.navigate).toHaveBeenCalledWith('/assistant/session-example-1');
    vi.clearAllMocks();
    mocks.isMobile = true;
    rerender(<AgentWorkspace sessionId="session-example-1" />);
    fireEvent.click(screen.getByRole('button', { name: '历史会话' }));
    expect(screen.getAllByText('当前会话').length).toBeGreaterThan(0);
    expect(mocks.workspace.stopActiveStream).not.toHaveBeenCalled();
  });

  it('登录回跳后只提交一次首页内存草稿', async () => {
    setPendingAgentDraft('推荐一部电影');
    render(<AgentWorkspace sessionId="session-example-1" />);
    await vi.waitFor(() => {
      expect(mocks.workspace.submit).toHaveBeenCalledOnce();
    });
    expect(mocks.workspace.submit).toHaveBeenCalledWith('推荐一部电影');
  });

  it('等待位置时禁用新消息并保留取消原运行入口', () => {
    mocks.workspace.projection = {
      ...mocks.workspace.projection,
      runId: 'run-waiting-location',
      status: 'WAITING_LOCATION',
    };

    render(<AgentWorkspace sessionId="session-example-1" />);

    expect(screen.getByText(/等待位置/)).toBeInTheDocument();
    expect(screen.getByLabelText('观影需求')).toBeDisabled();
    expect(screen.getByRole('button', { name: '取消运行' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '处理中' })).toBeDisabled();
  });

  it('推荐工作区选择方案后发起第二轮解释请求，不自行生成选座入口', async () => {
    mocks.workspace.projection.items = [
      {
        key: 'plan-latest',
        kind: 'plan-card',
        title: '周末观影方案',
        text: '以下内容来自服务端卡片数据',
        plans: [
          {
            showId: '70001',
            movieId: '10001',
            cinemaId: '20001',
            planType: 'COMPREHENSIVE',
            movieName: '真实影片',
            cinemaName: '真实影院',
            price: '46.00',
            currency: 'CNY',
            startTime: '2026-08-09T19:30:00+08:00',
            rating: '8.6',
            reasons: ['时间合适', '评分较高'],
            source: 'recommendation-service',
            dataAt: '2026-08-08T10:00:00+08:00',
            expiresAt: '2099-08-08T10:30:00+08:00',
            expired: false,
            purchaseEligible: true,
            distanceMeters: 1200,
          },
        ],
      },
    ];

    render(<AgentWorkspace sessionId="session-example-1" variant="recommendations" />);

    expect(screen.getByText('真实影片')).toBeInTheDocument();
    expect(screen.getByText('真实影院')).toBeInTheDocument();
    expect(screen.getByText('¥46.00')).toBeInTheDocument();
    expect(screen.queryByText(/1200|距离|路线|餐饮/)).not.toBeInTheDocument();
    expect(
      screen.getByText('已生成 1 个真实方案，请在左侧方案区选择后继续交流。'),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /综合推荐/ }));
    expect(mocks.workspace.submit).toHaveBeenCalledWith(
      '基于当前最新推荐中的第 1 个方案，请解释这个方案，并说明是否需要继续调整。',
    );
    expect(screen.queryByRole('link', { name: /去选座/ })).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('观影需求'), {
      target: { value: '换一家影院' },
    });
    fireEvent.click(screen.getByRole('button', { name: /发.*送/ }));
    expect(screen.getByLabelText('观影需求')).toHaveValue('');
    await vi.waitFor(() => {
      expect(mocks.workspace.submit).toHaveBeenLastCalledWith('换一家影院');
    });
  });

  it('消息提交被拒绝时恢复已立即清空的输入内容', async () => {
    let finishSubmission!: (sent: boolean) => void;
    mocks.workspace.submit.mockImplementation(
      () =>
        new Promise<boolean>((resolve) => {
          finishSubmission = resolve;
        }),
    );

    render(<AgentWorkspace sessionId="session-example-1" />);
    fireEvent.change(screen.getByLabelText('观影需求'), { target: { value: '换一家影院' } });
    fireEvent.click(screen.getByRole('button', { name: /发.*送/ }));
    expect(screen.getByLabelText('观影需求')).toHaveValue('');

    finishSubmission(false);
    await vi.waitFor(() => {
      expect(screen.getByLabelText('观影需求')).toHaveValue('换一家影院');
    });
  });

  it('没有当前 SELECT_SEATS 意图时不显示旧选座入口', () => {
    mocks.workspace.projection.items = [
      {
        key: 'old-select-seats',
        kind: 'business-intent',
        text: '旧方案选座入口',
        selectSeatsPath: '/shows/old/seats?movieId=old&cinemaId=old',
      },
      {
        key: 'plan-only',
        kind: 'plan-card',
        title: '推荐方案',
        text: '方案快照',
        plans: [],
      },
    ];

    render(<AgentWorkspace sessionId="session-example-1" variant="recommendations" />);

    expect(screen.queryByRole('link', { name: /去选座/ })).not.toBeInTheDocument();
  });
});
