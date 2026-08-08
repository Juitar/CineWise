import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import processingEventFixture from '../../../../backend/src/test/resources/fixtures/agent/c/processing-event.json';
import orderConfirmCardFixture from '../../../../backend/src/test/resources/fixtures/agent/c/order-confirm-card.json';
import { ApiError } from '../../shared/api/ApiError';
import { notifySessionEnding } from '../../shared/auth/sessionLifecycle';
import { parseAgentEvent } from './contract';
import type { AgentRunSnapshot } from './types';

const mocks = vi.hoisted(() => ({
  cancelAgentRun: vi.fn(),
  confirmAgentAction: vi.fn(),
  clearAgentSession: vi.fn(),
  clearAllAgentSessions: vi.fn(),
  createAgentSession: vi.fn(),
  getAgentRun: vi.fn(),
  listAgentMessages: vi.fn(),
  listAgentSessions: vi.fn(),
  postAgentStream: vi.fn(),
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => ({ status: 'authenticated' }),
}));

vi.mock('./api', () => ({
  cancelAgentRun: mocks.cancelAgentRun,
  confirmAgentAction: mocks.confirmAgentAction,
  clearAgentSession: mocks.clearAgentSession,
  clearAllAgentSessions: mocks.clearAllAgentSessions,
  createAgentSession: mocks.createAgentSession,
  getAgentRun: mocks.getAgentRun,
  listAgentMessages: mocks.listAgentMessages,
  listAgentSessions: mocks.listAgentSessions,
}));

vi.mock('./sse', () => ({ postAgentStream: mocks.postAgentStream }));

import { useAgentSessionBootstrap, useAgentWorkspace } from './useAgentWorkspace';

const processingEvent = parseAgentEvent(processingEventFixture);
const emptyMessages = { total: 0, page: 1, size: 100, records: [] };
const sessionPage = {
  total: 1,
  page: 1,
  size: 100,
  records: [
    {
      sessionId: 'session-example-1',
      summary: '测试会话',
      status: 'ACTIVE',
      createdAt: '2026-08-05T10:00:00+08:00',
      updatedAt: '2026-08-05T10:00:00+08:00',
    },
  ],
};

function snapshot(status: AgentRunSnapshot['status'], lastEventId = '40'): AgentRunSnapshot {
  return {
    runId: 'run-example-1',
    sessionId: 'session-example-1',
    status,
    planId: null,
    planVersion: 1,
    startedAt: null,
    finishedAt:
      status === 'RUNNING' || status === 'WAITING_LOCATION' ? null : '2026-08-05T10:00:01+08:00',
    lastEventId,
    messages: [],
    steps: [],
    events: [],
  };
}

function confirmationSnapshot(
  confirmationStatus: 'PENDING_CONFIRMATION' | 'EXECUTING' | 'SUCCEEDED' | 'INVALIDATED',
): AgentRunSnapshot {
  return {
    ...snapshot(confirmationStatus === 'EXECUTING' ? 'RUNNING' : 'COMPLETED'),
    runId: 'run-1',
    events: [
      parseAgentEvent({
        ...orderConfirmCardFixture,
        sessionId: 'session-example-1',
        runId: 'run-1',
        payload: { ...orderConfirmCardFixture.payload, status: confirmationStatus },
      }),
    ],
  };
}

beforeEach(() => {
  mocks.listAgentMessages.mockResolvedValue(emptyMessages);
  mocks.listAgentSessions.mockResolvedValue(sessionPage);
  mocks.getAgentRun.mockResolvedValue(snapshot('COMPLETED'));
});

afterEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe('useAgentWorkspace 状态与恢复', () => {
  const confirmationMessage = {
    messageId: 'message-confirm-1',
    runId: 'run-1',
    role: 'ASSISTANT',
    type: 'PLAN_CARD',
    text: '请确认建单',
    payload: orderConfirmCardFixture.payload,
    status: 'COMPLETED',
    completedAt: '2026-08-05T16:30:00+08:00',
    createdAt: '2026-08-05T16:30:00+08:00',
  };
  it('刷新时恢复普通用户文本和只读问题历史，但隐藏问题卡回答消息', async () => {
    mocks.listAgentMessages.mockResolvedValue({
      total: 3,
      page: 1,
      size: 100,
      records: [
        {
          messageId: 'message-user-1',
          runId: 'run-1',
          role: 'USER',
          type: 'TEXT',
          text: '推荐电影',
          payload: null,
          status: 'COMPLETED',
          completedAt: null,
          createdAt: '2026-08-08T10:00:00+08:00',
        },
        {
          messageId: 'message-question-1',
          runId: 'run-1',
          role: 'ASSISTANT',
          type: 'QUESTION',
          text: '请补充观影偏好',
          payload: { missingSlot: 'preference' },
          status: 'COMPLETED',
          completedAt: '2026-08-08T10:00:01+08:00',
          createdAt: '2026-08-08T10:00:01+08:00',
        },
        {
          messageId: 'message-question-answer-1',
          runId: 'run-2',
          role: 'USER',
          type: 'TEXT',
          text: '明天',
          payload: { entry: 'question' },
          status: 'COMPLETED',
          completedAt: null,
          createdAt: '2026-08-08T10:00:02+08:00',
        },
      ],
    });

    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    expect(result.current.projection.items).toEqual([
      expect.objectContaining({ kind: 'user-text', text: '推荐电影' }),
      expect.objectContaining({ kind: 'question', text: '请补充观影偏好' }),
    ]);
    expect(result.current.projection.safeError).toBeNull();
  });

  it.each([
    [401, 201006, '登录状态已失效'],
    [403, undefined, '没有权限'],
    [404, 206005, '会话不存在'],
    [409, 206008, '会话仍在运行'],
  ])('加载错误 %s 显示固定安全提示', async (status, code, expected) => {
    mocks.listAgentMessages.mockRejectedValue(
      new ApiError('raw backend message', { kind: 'HTTP', status, code }),
    );
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('error'));
    expect(result.current.feedback).toContain(expected);
    expect(result.current.feedback).not.toContain('raw backend message');
  });

  it('网络错误保留固定页面提示', async () => {
    mocks.listAgentMessages.mockRejectedValue(new ApiError('raw', { kind: 'NETWORK' }));
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('error'));
    expect(result.current.feedback).toBe('网络连接失败，已保留当前内容');
  });

  it('新工作区只创建一次会话', async () => {
    mocks.createAgentSession.mockResolvedValue(sessionPage.records[0]);
    const { result } = renderHook(() => useAgentSessionBootstrap(true));
    await waitFor(() => expect(result.current.createdSessionId).toBe('session-example-1'));
    expect(mocks.createAgentSession).toHaveBeenCalledOnce();
  });

  it('首个事件前断线进入 RESULT_UNKNOWN，且不自动重发', async () => {
    mocks.postAgentStream.mockRejectedValue(new ApiError('network', { kind: 'NETWORK' }));
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    await act(async () => {
      await result.current.submit('推荐电影');
    });
    expect(result.current.projection.status).toBe('RESULT_UNKNOWN');
    expect(mocks.postAgentStream).toHaveBeenCalledOnce();
    expect(mocks.postAgentStream.mock.calls[0][1]).toMatchObject({
      content: '推荐电影',
      context: { entry: 'workspace' },
    });
    expect(mocks.getAgentRun).not.toHaveBeenCalled();
  });

  it('首事件前收到 409 明确拒绝时进入失败而不是结果未知', async () => {
    mocks.postAgentStream.mockRejectedValue(
      new ApiError('running', { kind: 'HTTP', status: 409, code: 206008 }),
    );
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    await act(async () => {
      await result.current.submit('推荐电影');
    });
    expect(result.current.projection.status).toBe('FAILED');
    expect(result.current.projection.safeError).toContain('会话仍在运行');
  });

  it('退出登录开始时立即中止活动 SSE', async () => {
    const signalHolder: { current?: AbortSignal } = {};
    mocks.postAgentStream.mockImplementation(async (_session, _request, _cursor, signal) => {
      signalHolder.current = signal;
      await new Promise<void>((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')), {
          once: true,
        });
      });
    });
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    const submission = result.current.submit('推荐电影');
    await waitFor(() => expect(signalHolder.current).toBeDefined());
    act(() => notifySessionEnding());
    expect(signalHolder.current?.aborted).toBe(true);
    await act(async () => {
      await submission;
    });
  });

  it('活动请求期间拒绝重复提交', async () => {
    let finish!: () => void;
    mocks.postAgentStream.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve;
        }),
    );
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    let first!: Promise<boolean>;
    act(() => {
      first = result.current.submit('第一次');
    });
    await waitFor(() => expect(result.current.projection.status).toBe('CONNECTING'));
    await expect(result.current.submit('第二次')).resolves.toBe(false);
    expect(mocks.postAgentStream).toHaveBeenCalledOnce();
    finish();
    await act(async () => {
      await first;
    });
  });

  it('问题卡回答标记 question 来源且不追加独立用户气泡', async () => {
    mocks.postAgentStream.mockImplementation(
      async (_session, _request, _cursor, _signal, handlers) => {
        await handlers.onEvent(processingEvent);
      },
    );
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));

    await act(async () => {
      await result.current.submitQuestionAnswer('明天');
    });

    expect(mocks.postAgentStream).toHaveBeenCalledWith(
      'session-example-1',
      expect.objectContaining({ content: '明天', context: { entry: 'question' } }),
      '0',
      expect.any(AbortSignal),
      expect.any(Object),
    );
    expect(result.current.projection.items).not.toContainEqual(
      expect.objectContaining({ kind: 'user-text', text: '明天' }),
    );
  });

  it('终态后第二轮消息保留会话游标并绑定新的 runId 和 planVersion', async () => {
    let streamCount = 0;
    mocks.postAgentStream.mockImplementation(
      async (_session, _request, cursor, _signal, handlers) => {
        streamCount += 1;
        const runId = streamCount === 1 ? 'run-first' : 'run-second';
        const planVersion = streamCount === 1 ? 2 : 1;
        const firstEventId = streamCount === 1 ? '100' : '102';
        const completeEventId = streamCount === 1 ? '101' : '103';
        expect(cursor).toBe(streamCount === 1 ? '0' : '101');
        await handlers.onEvent(
          parseAgentEvent({
            eventId: firstEventId,
            sessionId: 'session-example-1',
            runId,
            planId: `plan-${streamCount}`,
            planVersion,
            nodeId: 'recommend',
            eventType: 'step.start',
            displayText: '正在处理',
            payload: { status: 'RUNNING' },
            occurredAt: '2026-08-08T10:00:00+08:00',
          }),
        );
        await handlers.onEvent(
          parseAgentEvent({
            eventId: completeEventId,
            sessionId: 'session-example-1',
            runId,
            planId: `plan-${streamCount}`,
            planVersion,
            nodeId: null,
            eventType: 'run.complete',
            displayText: '运行完成',
            payload: { status: 'COMPLETED' },
            occurredAt: '2026-08-08T10:00:01+08:00',
          }),
        );
      },
    );
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));

    await act(async () => {
      await result.current.submit('第一次推荐');
    });
    expect(result.current.projection).toMatchObject({
      runId: 'run-first',
      planVersion: 2,
      lastEventId: '101',
      status: 'COMPLETED',
    });

    await act(async () => {
      await result.current.submit('换一家影院');
    });
    expect(result.current.projection).toMatchObject({
      runId: 'run-second',
      planVersion: 1,
      lastEventId: '103',
      status: 'COMPLETED',
    });
    expect(mocks.postAgentStream).toHaveBeenCalledTimes(2);
  });

  it('确认重复点击只发送一次', async () => {
    mocks.listAgentMessages.mockResolvedValue({
      ...emptyMessages,
      total: 1,
      records: [confirmationMessage],
    });
    let resolveConfirmation!: (value: unknown) => void;
    mocks.confirmAgentAction.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveConfirmation = resolve;
        }),
    );
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    const itemKey = result.current.projection.items[0].key;
    act(() => {
      void result.current.confirm(itemKey, true);
      void result.current.confirm(itemKey, true);
    });
    expect(mocks.confirmAgentAction).toHaveBeenCalledOnce();
    await act(async () =>
      resolveConfirmation({
        actionId: 'action-1',
        runId: 'run-1',
        planVersion: 2,
        status: 'SUCCEEDED',
        updatedAt: '2026-08-05T16:31:00+08:00',
      }),
    );
    expect(result.current.projection.items[0].confirmation?.status).toBe('SUCCEEDED');
  });

  it('刷新历史确认卡时按消息 runId 恢复终态而不重新启用按钮', async () => {
    mocks.listAgentMessages.mockResolvedValue({
      ...emptyMessages,
      total: 1,
      records: [confirmationMessage],
    });
    mocks.getAgentRun.mockResolvedValue(confirmationSnapshot('SUCCEEDED'));

    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));

    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    expect(mocks.getAgentRun).toHaveBeenCalledWith('run-1');
    expect(result.current.projection.items[0].confirmation?.status).toBe('SUCCEEDED');
  });

  it('确认超时后重新拉历史消息并按消息 runId 查询真实状态', async () => {
    mocks.listAgentMessages.mockResolvedValue({
      ...emptyMessages,
      total: 1,
      records: [confirmationMessage],
    });
    mocks.confirmAgentAction.mockRejectedValue(
      new ApiError('timeout', { kind: 'TIMEOUT', isResultUnknown: true }),
    );
    mocks.getAgentRun
      .mockResolvedValueOnce(confirmationSnapshot('PENDING_CONFIRMATION'))
      .mockResolvedValue(confirmationSnapshot('SUCCEEDED'));
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    await act(async () => result.current.confirm(result.current.projection.items[0].key, true));
    expect(mocks.confirmAgentAction).toHaveBeenCalledOnce();
    expect(mocks.listAgentMessages).toHaveBeenCalledTimes(2);
    expect(mocks.getAgentRun).toHaveBeenCalledWith('run-1');
    expect(
      result.current.projection.items.find((item) => item.confirmation)?.confirmation?.status,
    ).toBe('SUCCEEDED');
  });

  it.each([
    [401, 201006, 'PENDING_CONFIRMATION', '登录状态已失效，请重新登录'],
    [403, undefined, 'INVALIDATED', '该确认操作不可用'],
    [404, 206005, 'INVALIDATED', '该确认操作不可用'],
  ] as const)(
    '确认接口返回 %s 时显示固定提示并进入安全状态',
    async (status, code, expectedStatus, expectedFeedback) => {
      mocks.listAgentMessages.mockResolvedValue({
        ...emptyMessages,
        total: 1,
        records: [confirmationMessage],
      });
      mocks.confirmAgentAction.mockRejectedValue(
        new ApiError('raw backend message', { kind: 'HTTP', status, code }),
      );
      const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
      await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
      await act(async () => result.current.confirm(result.current.projection.items[0].key, true));
      expect(result.current.feedback).toBe(expectedFeedback);
      expect(result.current.feedback).not.toContain('raw backend message');
      expect(result.current.projection.items[0].confirmation?.status).toBe(expectedStatus);
      expect(mocks.confirmAgentAction).toHaveBeenCalledOnce();
      expect(mocks.getAgentRun).toHaveBeenCalledTimes(1);
    },
  );

  it.each([
    [409, 206006, 'EXECUTING', '确认操作正在处理，请勿重复提交'],
    [409, 206004, 'INVALIDATED', '确认内容已失效'],
  ] as const)(
    '确认接口返回 %s 时查询运行并恢复为 %s',
    async (status, code, confirmationStatus, expectedFeedback) => {
      mocks.listAgentMessages.mockResolvedValue({
        ...emptyMessages,
        total: 1,
        records: [confirmationMessage],
      });
      mocks.confirmAgentAction.mockRejectedValue(
        new ApiError('raw backend message', { kind: 'HTTP', status, code }),
      );
      mocks.getAgentRun
        .mockResolvedValueOnce(confirmationSnapshot('PENDING_CONFIRMATION'))
        .mockResolvedValue(confirmationSnapshot(confirmationStatus));
      const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
      await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
      await act(async () => result.current.confirm(result.current.projection.items[0].key, true));
      expect(result.current.feedback).toBe(expectedFeedback);
      expect(result.current.projection.items[0].confirmation?.status).toBe(confirmationStatus);
      expect(mocks.getAgentRun).toHaveBeenCalledWith('run-1');
      expect(mocks.confirmAgentAction).toHaveBeenCalledOnce();
    },
  );

  it('确认接口返回 5xx 时不重发 POST 并按历史消息 runId 恢复', async () => {
    mocks.listAgentMessages.mockResolvedValue({
      ...emptyMessages,
      total: 1,
      records: [confirmationMessage],
    });
    mocks.confirmAgentAction.mockRejectedValue(
      new ApiError('service unavailable', { kind: 'HTTP', status: 503 }),
    );
    mocks.getAgentRun
      .mockResolvedValueOnce(confirmationSnapshot('PENDING_CONFIRMATION'))
      .mockResolvedValue(confirmationSnapshot('SUCCEEDED'));
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    await act(async () => result.current.confirm(result.current.projection.items[0].key, true));
    expect(mocks.confirmAgentAction).toHaveBeenCalledOnce();
    expect(mocks.listAgentMessages).toHaveBeenCalledTimes(2);
    expect(mocks.getAgentRun).toHaveBeenCalledWith('run-1');
    expect(result.current.projection.items[0].confirmation?.status).toBe('SUCCEEDED');
  });

  it('已有 runId 断线时先查询原运行并按快照完成', async () => {
    mocks.postAgentStream.mockImplementation(
      async (_session, _request, _cursor, _signal, handlers) => {
        await handlers.onEvent(processingEvent);
        throw new ApiError('network', { kind: 'NETWORK' });
      },
    );
    mocks.getAgentRun.mockResolvedValue(snapshot('COMPLETED'));
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    await act(async () => {
      await result.current.submit('推荐电影');
    });
    expect(mocks.getAgentRun).toHaveBeenCalledWith('run-example-1');
    expect(result.current.projection.status).toBe('COMPLETED');
  });

  it('断线恢复到等待位置时保留原运行且不重连流', async () => {
    mocks.postAgentStream.mockImplementation(
      async (_session, _request, _cursor, _signal, handlers) => {
        await handlers.onEvent(processingEvent);
        throw new ApiError('network', { kind: 'NETWORK' });
      },
    );
    mocks.getAgentRun.mockResolvedValue(snapshot('WAITING_LOCATION'));
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));

    await act(async () => {
      await result.current.submit('按距离推荐电影');
    });

    expect(mocks.getAgentRun).toHaveBeenCalledWith('run-example-1');
    expect(mocks.postAgentStream).toHaveBeenCalledOnce();
    expect(result.current.projection).toMatchObject({
      runId: 'run-example-1',
      status: 'WAITING_LOCATION',
    });
    await expect(result.current.submit('再发一条')).resolves.toBe(false);
    expect(mocks.postAgentStream).toHaveBeenCalledOnce();
  });

  it('主动取消使用服务端返回的终态', async () => {
    mocks.postAgentStream.mockImplementation(
      async (_session, _request, _cursor, _signal, handlers) => {
        await handlers.onEvent(processingEvent);
        await new Promise<void>(() => {
          // 保持活动连接，直到测试调用取消。
        });
      },
    );
    mocks.cancelAgentRun.mockResolvedValue({
      runId: 'run-example-1',
      status: 'CANCELLED',
      finishedAt: '2026-08-05T10:00:01+08:00',
    });
    const { result } = renderHook(() => useAgentWorkspace('session-example-1'));
    await waitFor(() => expect(result.current.loadStatus).toBe('ready'));
    act(() => {
      void result.current.submit('推荐电影');
    });
    await waitFor(() => expect(result.current.projection.runId).toBe('run-example-1'));
    await act(async () => {
      await result.current.cancel();
    });
    expect(result.current.projection.status).toBe('CANCELLED');
    expect(mocks.cancelAgentRun).toHaveBeenCalledWith('run-example-1');
  });

  it('20 秒无事件或心跳时查询原运行并使用原游标续传', async () => {
    vi.useFakeTimers();
    mocks.getAgentRun.mockResolvedValue(snapshot('RUNNING', '42'));
    mocks.postAgentStream.mockImplementation(
      async (_session, _request, _cursor, signal, handlers) => {
        if (mocks.postAgentStream.mock.calls.length === 1) await handlers.onEvent(processingEvent);
        await new Promise<void>((_resolve, reject) => {
          signal.addEventListener(
            'abort',
            () => reject(new DOMException('aborted', 'AbortError')),
            {
              once: true,
            },
          );
        });
      },
    );
    const { result, unmount } = renderHook(() => useAgentWorkspace('session-example-1'));
    await act(async () => {
      await Promise.resolve();
    });
    const submission = result.current.submit('推荐电影');
    await act(async () => {
      await Promise.resolve();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(20_000);
    });
    expect(mocks.getAgentRun).toHaveBeenCalledWith('run-example-1');
    expect(mocks.postAgentStream).toHaveBeenCalledTimes(2);
    expect(mocks.postAgentStream.mock.calls[1][2]).toBe('42');
    unmount();
    await act(async () => {
      await submission;
    });
  });
});
