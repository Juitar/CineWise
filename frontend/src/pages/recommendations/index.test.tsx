import { cleanup, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  params: {} as { sessionId?: string },
  bootstrap: {
    createdSessionId: null as string | null,
    error: null as string | null,
    resultUnknown: false,
    retry: vi.fn(),
  },
}));

vi.mock('umi', () => ({
  Link: ({ children, to }: React.PropsWithChildren<{ to: string }>) => <a href={to}>{children}</a>,
  useNavigate: () => mocks.navigate,
  useParams: () => mocks.params,
}));
vi.mock('../../features/agent-workspace', () => ({
  AgentWorkspace: ({ sessionId, variant }: { sessionId: string; variant: string }) => (
    <div>{`${variant}:${sessionId}`}</div>
  ),
}));
vi.mock('../../modules/agent/useAgentWorkspace', () => ({
  useAgentSessionBootstrap: () => mocks.bootstrap,
}));

import RecommendationsPage from './index';

beforeEach(() => {
  mocks.params = {};
  mocks.bootstrap.createdSessionId = null;
  mocks.bootstrap.error = null;
  mocks.bootstrap.resultUnknown = false;
  vi.clearAllMocks();
});

afterEach(() => cleanup());

describe('RecommendationsPage', () => {
  it('创建会话后进入 recommendations 会话路由', () => {
    mocks.bootstrap.createdSessionId = 'session example';
    render(<RecommendationsPage />);
    expect(mocks.navigate).toHaveBeenCalledWith('/recommendations/session%20example', {
      replace: true,
    });
  });

  it('已有会话时加载推荐工作区模式', () => {
    mocks.params = { sessionId: 'session-example-1' };
    render(<RecommendationsPage />);
    expect(screen.getByText('recommendations:session-example-1')).toBeInTheDocument();
  });
});
