import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  isMobile: false,
  navigate: vi.fn(),
  workspace: {
    cancel: vi.fn(),
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
      status: 'IDLE' as const,
      safeError: null as string | null,
      items: [] as Array<{
        key: string;
        kind: string;
        text: string;
        title?: string;
        fields?: Array<{ label: string; value: string }>;
      }>,
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

  it('未知卡片安全降级且危险 HTML 只作为文本显示', () => {
    mocks.workspace.projection.items = [
      { key: '1', kind: 'card-placeholder', text: '推荐卡片数据暂不完整' },
      { key: '2', kind: 'assistant-text', text: '<img src=x onerror=alert(1)>' },
    ];
    const { container } = render(<AgentWorkspace sessionId="session-example-1" />);
    expect(screen.getByText('推荐卡片数据暂不完整')).toBeInTheDocument();
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
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
    fireEvent.click(screen.getByRole('button', { name: '会话列表' }));
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
});
