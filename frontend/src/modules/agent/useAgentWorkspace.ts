import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import { useAuth } from '../../shared/auth/AuthProvider';
import { registerSessionEndHandler } from '../../shared/auth/sessionLifecycle';
import {
  cancelAgentRun,
  clearAgentSession,
  clearAllAgentSessions,
  createAgentSession,
  confirmAgentAction,
  getAgentRun,
  listAgentMessages,
  listAgentSessions,
} from './api';
import { AgentContractError } from './contract';
import {
  buildProjectionFromHistoryAndSnapshots,
  travelAdviceRecoveryRunIds,
  buildProjectionFromSnapshot,
  consumeAgentEvent,
  createAgentProjection,
  deduplicateConfirmationItems,
  updateConfirmationItem,
} from './projection';
import type { AgentDisplayItem, AgentProjection } from './projection';
import { recoverFromStreamReset } from './recovery';
import { postAgentStream } from './sse';
import type { AgentMessage, AgentSession, AgentStreamRequest } from './types';

type LoadStatus = 'error' | 'loading' | 'ready';
type AgentSubmissionEntry = 'question' | 'workspace';

interface AgentWorkspaceCacheState {
  sessions: readonly AgentSession[];
  projection: AgentProjection;
  loadStatus: LoadStatus;
  feedback: string | null;
}

interface AgentWorkspaceRuntime {
  readonly sessionId: string;
  state: AgentWorkspaceCacheState;
  controller: AbortController | null;
  sequence: number;
  inactivity: number | null;
  loading: Promise<void> | null;
  readonly subscribers: Set<() => void>;
}

/**
 * 路由切换时 Agent 面板会短暂卸载；同一会话的投影和活动 SSE 不能跟着销毁。
 * 这里仅保存页面内存，不写入 localStorage，也不把任何 Agent 数据扩散到 URL。
 */
const workspaceRuntimes = new Map<string, AgentWorkspaceRuntime>();

function runtimeFor(sessionId: string): AgentWorkspaceRuntime {
  const existing = workspaceRuntimes.get(sessionId);
  if (existing !== undefined) return existing;
  const runtime: AgentWorkspaceRuntime = {
    sessionId,
    state: {
      sessions: [],
      projection: createAgentProjection(sessionId),
      loadStatus: 'loading',
      feedback: null,
    },
    controller: null,
    sequence: 0,
    inactivity: null,
    loading: null,
    subscribers: new Set(),
  };
  workspaceRuntimes.set(sessionId, runtime);
  return runtime;
}

function updateRuntime(
  runtime: AgentWorkspaceRuntime,
  update: Partial<AgentWorkspaceCacheState>,
): void {
  runtime.state = { ...runtime.state, ...update };
  runtime.subscribers.forEach((subscriber) => subscriber());
}

/** 仅供 Hook 单测隔离模块级会话缓存，业务代码不得调用。 */
export function resetAgentWorkspaceCacheForTest(): void {
  workspaceRuntimes.forEach((runtime) => {
    runtime.controller?.abort();
    if (runtime.inactivity !== null) window.clearTimeout(runtime.inactivity);
  });
  workspaceRuntimes.clear();
}

function safeErrorMessage(error: unknown): string {
  if (error instanceof AgentContractError) return error.message;
  if (!(error instanceof ApiError)) return 'Agent 暂时不可用，请稍后重试';
  if (error.status === 401 || error.code === 201006) return '登录状态已失效，请重新登录';
  if (error.status === 403) return '你没有权限访问这个会话';
  if (error.status === 404 || error.code === 206005) return '会话不存在或已被清空';
  if (error.status === 409 || error.code === 206008) return '会话仍在运行，请先取消或等待完成';
  if (error.kind === 'NETWORK') return '网络连接失败，已保留当前内容';
  return 'Agent 请求未成功，请稍后重试';
}

function confirmationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return '确认请求未完成，请稍后查看实际状态';
  if (error.status === 401 || error.code === 201006) return '登录状态已失效，请重新登录';
  if (error.status === 403 || error.status === 404 || error.code === 206005)
    return '该确认操作不可用';
  if (error.code === 206004 || error.status === 422) return '确认内容已失效';
  if (error.code === 206006 || error.status === 409) return '确认操作正在处理，请勿重复提交';
  return '确认请求未完成，请稍后查看实际状态';
}

/** 为 `/assistant` 创建一次会话；页面只负责在成功后替换路由。 */
export function useAgentSessionBootstrap(enabled: boolean) {
  const [createdSessionId, setCreatedSessionId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [resultUnknown, setResultUnknown] = useState(false);
  const creatingRef = useRef(false);

  const create = useCallback(async () => {
    if (!enabled || creatingRef.current) return;
    creatingRef.current = true;
    setError(null);
    setResultUnknown(false);
    try {
      const session = await createAgentSession();
      setCreatedSessionId(session.sessionId);
    } catch (createError) {
      const unknown = createError instanceof ApiError && createError.isResultUnknown;
      if (!unknown) creatingRef.current = false;
      setResultUnknown(unknown);
      setError(unknown ? '会话创建结果暂时无法确认，请先返回首页' : safeErrorMessage(createError));
    }
  }, [enabled]);

  useEffect(() => {
    void create();
  }, [create]);

  return { createdSessionId, error, resultUnknown, retry: create };
}

function createClientRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID();
  const bytes = new Uint8Array(16);
  if (typeof globalThis.crypto?.getRandomValues === 'function') {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256);
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function isTerminal(status: AgentProjection['status']): boolean {
  return ['CANCELLED', 'COMPLETED', 'FAILED'].includes(status);
}

function withoutLocalThinking(
  projection: AgentProjection,
  localThinkingKey: string,
): AgentProjection {
  return {
    ...projection,
    items: projection.items.filter((item) => item.key !== localThinkingKey),
  };
}

function cardRecoveryRunIds(messages: readonly AgentMessage[]): readonly string[] {
  return Array.from(
    new Set([
      ...messages
        .filter((message) => typeof message.payload?.actionId === 'string')
        .map((message) => message.runId),
      ...messages
        .filter((message) => ['MOVIE_CARD', 'PLAN_CARD'].includes(message.type.toUpperCase()))
        .map((message) => message.runId),
      ...travelAdviceRecoveryRunIds(messages),
    ]),
  );
}

/**
 * 管理一个 Agent 工作区的会话、唯一活动流、投影和只读恢复。
 * 页面和桌面/移动视图只消费该 Hook，不各自保存运行游标。
 */
export function useAgentWorkspace(sessionId: string) {
  const { status: authStatus } = useAuth();
  const runtime = runtimeFor(sessionId);
  const [, setRenderVersion] = useState(0);
  const projectionRef = useRef(runtime.state.projection);
  if (projectionRef.current.sessionId !== sessionId) {
    projectionRef.current = runtime.state.projection;
  }
  const confirmingActionIdsRef = useRef(new Set<string>());

  useEffect(() => {
    projectionRef.current = runtime.state.projection;
    const subscriber = () => {
      projectionRef.current = runtime.state.projection;
      setRenderVersion((version) => version + 1);
    };
    runtime.subscribers.add(subscriber);
    return () => {
      runtime.subscribers.delete(subscriber);
    };
  }, [runtime]);

  const clearInactivity = useCallback(() => {
    const currentRuntime = runtimeFor(sessionId);
    if (currentRuntime.inactivity !== null) {
      window.clearTimeout(currentRuntime.inactivity);
      currentRuntime.inactivity = null;
    }
  }, [sessionId]);

  const stopActiveStream = useCallback(() => {
    const currentRuntime = runtimeFor(sessionId);
    currentRuntime.sequence += 1;
    currentRuntime.controller?.abort();
    currentRuntime.controller = null;
    clearInactivity();
  }, [clearInactivity, sessionId]);

  const refreshSessions = useCallback(async () => {
    const page = await listAgentSessions();
    updateRuntime(runtimeFor(sessionId), { sessions: page.records });
  }, [sessionId]);

  useEffect(() => {
    const currentRuntime = runtimeFor(sessionId);
    const hadCachedContent =
      currentRuntime.state.loadStatus === 'ready' ||
      currentRuntime.state.projection.items.length > 0 ||
      currentRuntime.state.sessions.length > 0;
    // 子路由回来时保留已显示的内容，在后台刷新即可；首次打开才显示加载态。
    if (!hadCachedContent) updateRuntime(currentRuntime, { loadStatus: 'loading', feedback: null });
    // 活动 SSE 是当前会话的最新来源，恢复历史不能覆盖它，也不能再开一条流。
    if (currentRuntime.controller !== null) return undefined;
    const load = async () => {
      try {
        const [messages, sessionPage] = await Promise.all([
          listAgentMessages(sessionId),
          listAgentSessions(),
        ]);
        const snapshots = await Promise.all(
          cardRecoveryRunIds(messages.records).map((runId) => getAgentRun(runId)),
        );
        if (currentRuntime.controller !== null) {
          updateRuntime(currentRuntime, { sessions: sessionPage.records });
          return;
        }
        const next = deduplicateConfirmationItems(
          buildProjectionFromHistoryAndSnapshots(sessionId, messages.records, snapshots),
        );
        projectionRef.current = next;
        updateRuntime(currentRuntime, {
          projection: next,
          sessions: sessionPage.records,
          loadStatus: 'ready',
          feedback: null,
        });
      } catch (error) {
        // 只读恢复失败不能把用户已经看到的会话清空或打回首页。
        if (hadCachedContent) {
          updateRuntime(currentRuntime, { feedback: safeErrorMessage(error) });
        } else {
          updateRuntime(currentRuntime, {
            projection: createAgentProjection(sessionId),
            feedback: safeErrorMessage(error),
            loadStatus: 'error',
          });
        }
      }
    };
    if (currentRuntime.loading === null) {
      currentRuntime.loading = load().finally(() => {
        if (currentRuntime.loading !== null) currentRuntime.loading = null;
      });
    }
    return undefined;
  }, [sessionId]);

  useEffect(() => {
    if (authStatus !== 'authenticated') stopActiveStream();
  }, [authStatus, stopActiveStream]);

  useEffect(() => registerSessionEndHandler(stopActiveStream), [stopActiveStream]);

  const replaceProjection = useCallback((next: AgentProjection) => {
    const deduplicated = deduplicateConfirmationItems(next);
    projectionRef.current = deduplicated;
    updateRuntime(runtimeFor(sessionId), { projection: deduplicated });
  }, [sessionId]);

  const recoverRun = useCallback(
    async (sequence: number, resume: (cursor: string) => Promise<void>) => {
      const current = projectionRef.current;
      if (!current.runId || sequence !== runtimeFor(sessionId).sequence) return;
      try {
        const [snapshot, history] = await Promise.all([
          getAgentRun(current.runId),
          listAgentMessages(current.sessionId),
        ]);
        if (sequence !== runtimeFor(sessionId).sequence) return;
        const rebuilt = buildProjectionFromSnapshot(snapshot, history.records);
        replaceProjection(rebuilt);
        if (snapshot.status === 'RUNNING') await resume(rebuilt.lastEventId);
      } catch (error) {
        if (sequence !== runtimeFor(sessionId).sequence) return;
        replaceProjection({
          ...projectionRef.current,
          status: 'RESULT_UNKNOWN',
          safeError: safeErrorMessage(error),
        });
      }
    },
    [replaceProjection],
  );

  const recoverConfirmation = useCallback(
    async (actionId: string) => {
      try {
        const history = await listAgentMessages(projectionRef.current.sessionId);
        const message = history.records.find((record) => record.payload?.actionId === actionId);
        if (!message) throw new AgentContractError('确认操作不在当前会话历史中');
        const snapshot = await getAgentRun(message.runId);
        replaceProjection(buildProjectionFromSnapshot(snapshot, history.records));
      } catch (error) {
        replaceProjection({ ...projectionRef.current, safeError: safeErrorMessage(error) });
      }
    },
    [replaceProjection],
  );

  const startStream = useCallback(
    async function connect(request: AgentStreamRequest, cursor: string | null, recovering = false) {
      stopActiveStream();
      const controller = new AbortController();
      const currentRuntime = runtimeFor(sessionId);
      currentRuntime.controller = controller;
      const sequence = currentRuntime.sequence;
      const localThinkingKey = `local-thinking:${request.clientRequestId}`;
      if (!recovering) {
        replaceProjection({ ...projectionRef.current, status: 'CONNECTING', safeError: null });
      }

      const resume = async (resumeCursor: string) => {
        if (sequence !== runtimeFor(sessionId).sequence) return;
        await connect(request, resumeCursor, true);
      };
      const armInactivity = () => {
        clearInactivity();
        currentRuntime.inactivity = window.setTimeout(() => {
          if (sequence !== runtimeFor(sessionId).sequence) return;
          controller.abort();
          currentRuntime.controller = null;
          void recoverRun(sequence, resume);
        }, 20_000);
      };
      armInactivity();

      try {
        await postAgentStream(sessionId, request, cursor, controller.signal, {
          onHeartbeat: armInactivity,
          onEvent: async (event) => {
            if (sequence !== runtimeFor(sessionId).sequence || event.sessionId !== sessionId) return;
            armInactivity();
            const current = withoutLocalThinking(projectionRef.current, localThinkingKey);
            const result = consumeAgentEvent(current, event);
            if (result.outcome === 'ignored') return;
            if (result.outcome === 'reset-required') {
              clearInactivity();
              try {
                const rebuilt = await recoverFromStreamReset(event, {
                  getRun: getAgentRun,
                  getMessages: (currentSessionId) => listAgentMessages(currentSessionId),
                });
                if (sequence !== runtimeFor(sessionId).sequence) return;
                replaceProjection(rebuilt);
                if (rebuilt.status === 'STREAMING') await resume(rebuilt.lastEventId);
              } catch (error) {
                replaceProjection({
                  ...projectionRef.current,
                  status: 'RESULT_UNKNOWN',
                  safeError: safeErrorMessage(error),
                });
              }
              return;
            }
            replaceProjection(result.projection);
            if (isTerminal(result.projection.status)) {
              clearInactivity();
              if (currentRuntime.controller === controller) currentRuntime.controller = null;
            }
          },
        });
        clearInactivity();
        if (currentRuntime.controller === controller) currentRuntime.controller = null;
        const current = projectionRef.current;
        if (!isTerminal(current.status)) {
          if (current.runId) await recoverRun(sequence, resume);
          else {
            replaceProjection({
              ...current,
              status: 'RESULT_UNKNOWN',
              safeError: '消息已发出，但暂时无法确认运行结果',
            });
          }
        }
      } catch (error) {
        clearInactivity();
        if (sequence !== runtimeFor(sessionId).sequence || controller.signal.aborted) return;
        if (currentRuntime.controller === controller) currentRuntime.controller = null;
        const current = withoutLocalThinking(projectionRef.current, localThinkingKey);
        if (current.runId) await recoverRun(sequence, resume);
        else {
          const definitelyRejected =
            error instanceof ApiError &&
            error.kind === 'HTTP' &&
            error.status !== undefined &&
            error.status < 500;
          replaceProjection({
            ...current,
            status: definitelyRejected ? 'FAILED' : 'RESULT_UNKNOWN',
            safeError: safeErrorMessage(error),
          });
        }
      }
    },
    [clearInactivity, recoverRun, replaceProjection, sessionId, stopActiveStream],
  );

  const submitMessage = useCallback(
    async (content: string, entry: AgentSubmissionEntry): Promise<boolean> => {
      const normalized = content.trim();
      if (
        !normalized ||
        normalized.length > 2000 ||
        runtimeFor(sessionId).controller !== null ||
        ['CONNECTING', 'STREAMING', 'WAITING_LOCATION', 'RESULT_UNKNOWN'].includes(
          projectionRef.current.status,
        )
      ) {
        return false;
      }
      const request: AgentStreamRequest = {
        clientRequestId: createClientRequestId(),
        content: normalized,
        context: { entry },
      };
      const localItem: AgentDisplayItem = {
        key: `local:${request.clientRequestId}`,
        kind: 'user-text',
        text: normalized,
      };
      const localThinkingItem: AgentDisplayItem = {
        key: `local-thinking:${request.clientRequestId}`,
        kind: 'thinking',
        text: '正在理解你的需求',
      };
      const previous = projectionRef.current;
      const visibleItems =
        entry === 'workspace'
          ? previous.items.filter((item) => item.kind !== 'question')
          : previous.items;
      const cursor = previous.lastEventId;
      // eventId 是会话级递增游标，而 runId/planVersion 只属于一次运行。
      // 终态后发送新消息必须解除旧运行绑定，否则新运行的全部事件都会被 reducer 忽略。
      replaceProjection({
        ...previous,
        runId: null,
        planVersion: null,
        items:
          entry === 'workspace'
            ? [...visibleItems, localItem, localThinkingItem]
            : [...visibleItems, localThinkingItem],
        safeError: null,
      });
      await startStream(request, cursor);
      return true;
    },
    [replaceProjection, startStream],
  );

  const submit = useCallback(
    (content: string): Promise<boolean> => submitMessage(content, 'workspace'),
    [submitMessage],
  );

  const submitQuestionAnswer = useCallback(
    (content: string): Promise<boolean> => submitMessage(content, 'question'),
    [submitMessage],
  );

  const cancel = useCallback(async () => {
    const runId = projectionRef.current.runId;
    if (!runId) return;
    stopActiveStream();
    try {
      const result = await cancelAgentRun(runId);
      replaceProjection({
        ...projectionRef.current,
        status: result.status === 'RUNNING' ? 'STREAMING' : result.status,
        safeError: null,
      });
    } catch (error) {
      replaceProjection({
        ...projectionRef.current,
        status: error instanceof ApiError && error.isResultUnknown ? 'RESULT_UNKNOWN' : 'FAILED',
        safeError: safeErrorMessage(error),
      });
    }
  }, [replaceProjection, stopActiveStream]);

  const confirm = useCallback(
    async (itemKey: string, confirmed: boolean): Promise<void> => {
      const item = projectionRef.current.items.find((candidate) => candidate.key === itemKey);
      const action = item?.confirmation;
      if (
        !action ||
        action.status !== 'PENDING_CONFIRMATION' ||
        action.submitting ||
        confirmingActionIdsRef.current.has(action.actionId)
      )
        return;
      confirmingActionIdsRef.current.add(action.actionId);
      replaceProjection(
        updateConfirmationItem(projectionRef.current, itemKey, { submitting: true }),
      );
      try {
        const result = await confirmAgentAction(action.actionId, confirmed);
        replaceProjection(updateConfirmationItem(projectionRef.current, itemKey, result));
        if (result.status === 'EXECUTING' || result.status === 'RESULT_UNKNOWN')
          await recoverConfirmation(action.actionId);
      } catch (error) {
        const unknown =
          error instanceof ApiError &&
          (error.isResultUnknown || (error.status !== undefined && error.status >= 500));
        if (unknown) {
          replaceProjection(
            updateConfirmationItem(projectionRef.current, itemKey, { status: 'RESULT_UNKNOWN' }),
          );
          await recoverConfirmation(action.actionId);
        } else {
          const status =
            error instanceof ApiError && error.code === 206004
              ? 'INVALIDATED'
              : error instanceof ApiError && [403, 404, 422].includes(error.status ?? 0)
                ? 'INVALIDATED'
                : error instanceof ApiError && error.status === 409
                  ? 'EXECUTING'
                  : null;
          replaceProjection(
            status
              ? updateConfirmationItem(projectionRef.current, itemKey, { status })
              : updateConfirmationItem(projectionRef.current, itemKey, { submitting: false }),
          );
          updateRuntime(runtimeFor(sessionId), { feedback: confirmationErrorMessage(error) });
          if (error instanceof ApiError && [409, 422].includes(error.status ?? 0)) {
            await recoverConfirmation(action.actionId);
          }
        }
      } finally {
        confirmingActionIdsRef.current.delete(action.actionId);
      }
    },
    [recoverConfirmation, replaceProjection],
  );

  const clearCurrent = useCallback(async () => {
    try {
      await clearAgentSession(sessionId);
      updateRuntime(runtimeFor(sessionId), { feedback: '当前会话已清空' });
      replaceProjection(createAgentProjection(sessionId));
      await refreshSessions();
      return true;
    } catch (error) {
      updateRuntime(runtimeFor(sessionId), { feedback: safeErrorMessage(error) });
      return false;
    }
  }, [refreshSessions, replaceProjection, sessionId]);

  /**
   * 单条会话删除复用既有 DELETE /sessions/{sessionId} 接口。
   * 后端会拒绝仍有运行中的会话，前端不把这个失败伪装成已删除。
   */
  const deleteSession = useCallback(
    async (targetSessionId: string): Promise<boolean> => {
      try {
        await clearAgentSession(targetSessionId);
        const targetRuntime = workspaceRuntimes.get(targetSessionId);
        targetRuntime?.controller?.abort();
        if (targetRuntime !== undefined && targetRuntime.inactivity !== null) {
          window.clearTimeout(targetRuntime.inactivity);
        }
        if (targetSessionId === sessionId) {
          replaceProjection(createAgentProjection(sessionId));
        } else {
          workspaceRuntimes.delete(targetSessionId);
        }
        updateRuntime(runtimeFor(sessionId), { feedback: '会话已删除' });
        await refreshSessions();
        return true;
      } catch (error) {
        updateRuntime(runtimeFor(sessionId), { feedback: safeErrorMessage(error) });
        return false;
      }
    },
    [refreshSessions, replaceProjection, sessionId],
  );

  const clearAll = useCallback(async () => {
    try {
      const result = await clearAllAgentSessions();
      updateRuntime(runtimeFor(sessionId), {
        feedback: `已清空 ${result.clearedCount} 个会话，跳过 ${result.skippedCount} 个运行中会话`,
      });
      await refreshSessions();
      return result;
    } catch (error) {
      updateRuntime(runtimeFor(sessionId), { feedback: safeErrorMessage(error) });
      return null;
    }
  }, [refreshSessions]);

  const createNewSession = useCallback(async () => {
    try {
      const created = await createAgentSession();
      await refreshSessions();
      return created;
    } catch (error) {
      updateRuntime(runtimeFor(sessionId), { feedback: safeErrorMessage(error) });
      return null;
    }
  }, [refreshSessions]);

  const { feedback, loadStatus, projection, sessions } = runtimeFor(sessionId).state;
  return {
    cancel,
    clearAll,
    clearCurrent,
    deleteSession,
    confirm,
    createNewSession,
    feedback,
    loadStatus,
    projection,
    refreshSessions,
    sessions,
    stopActiveStream,
    submit,
    submitQuestionAnswer,
  };
}
