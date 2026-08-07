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
  buildProjectionFromHistory,
  buildProjectionFromSnapshot,
  consumeAgentEvent,
  createAgentProjection,
  updateConfirmationItem,
} from './projection';
import type { AgentDisplayItem, AgentProjection } from './projection';
import { recoverFromStreamReset } from './recovery';
import { postAgentStream } from './sse';
import type { AgentSession, AgentStreamRequest } from './types';

type LoadStatus = 'error' | 'loading' | 'ready';

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

/**
 * 管理一个 Agent 工作区的会话、唯一活动流、投影和只读恢复。
 * 页面和桌面/移动视图只消费该 Hook，不各自保存运行游标。
 */
export function useAgentWorkspace(sessionId: string) {
  const { status: authStatus } = useAuth();
  const [sessions, setSessions] = useState<readonly AgentSession[]>([]);
  const [projection, setProjection] = useState<AgentProjection>(() =>
    createAgentProjection(sessionId),
  );
  const [loadStatus, setLoadStatus] = useState<LoadStatus>('loading');
  const [feedback, setFeedback] = useState<string | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const sequenceRef = useRef(0);
  const inactivityRef = useRef<number | null>(null);
  const projectionRef = useRef(projection);
  const confirmingActionIdsRef = useRef(new Set<string>());

  useEffect(() => {
    projectionRef.current = projection;
  }, [projection]);

  const clearInactivity = useCallback(() => {
    if (inactivityRef.current !== null) {
      window.clearTimeout(inactivityRef.current);
      inactivityRef.current = null;
    }
  }, []);

  const stopActiveStream = useCallback(() => {
    sequenceRef.current += 1;
    controllerRef.current?.abort();
    controllerRef.current = null;
    clearInactivity();
  }, [clearInactivity]);

  const refreshSessions = useCallback(async () => {
    const page = await listAgentSessions();
    setSessions(page.records);
  }, []);

  useEffect(() => {
    let active = true;
    stopActiveStream();
    setLoadStatus('loading');
    setFeedback(null);
    Promise.all([listAgentMessages(sessionId), listAgentSessions()])
      .then(([messages, sessionPage]) => {
        if (!active) return;
        const next = buildProjectionFromHistory(sessionId, messages.records);
        projectionRef.current = next;
        setProjection(next);
        setSessions(sessionPage.records);
        setLoadStatus('ready');
      })
      .catch((error: unknown) => {
        if (!active) return;
        setProjection(createAgentProjection(sessionId));
        setFeedback(safeErrorMessage(error));
        setLoadStatus('error');
      });
    return () => {
      active = false;
      stopActiveStream();
    };
  }, [sessionId, stopActiveStream]);

  useEffect(() => {
    if (authStatus !== 'authenticated') stopActiveStream();
  }, [authStatus, stopActiveStream]);

  useEffect(() => registerSessionEndHandler(stopActiveStream), [stopActiveStream]);

  const replaceProjection = useCallback((next: AgentProjection) => {
    projectionRef.current = next;
    setProjection(next);
  }, []);

  const recoverRun = useCallback(
    async (sequence: number, resume: (cursor: string) => Promise<void>) => {
      const current = projectionRef.current;
      if (!current.runId || sequence !== sequenceRef.current) return;
      try {
        const [snapshot, history] = await Promise.all([
          getAgentRun(current.runId),
          listAgentMessages(current.sessionId),
        ]);
        if (sequence !== sequenceRef.current) return;
        const rebuilt = buildProjectionFromSnapshot(snapshot, history.records);
        replaceProjection(rebuilt);
        if (snapshot.status === 'RUNNING') await resume(rebuilt.lastEventId);
      } catch (error) {
        if (sequence !== sequenceRef.current) return;
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
        const message = history.records.find((record) => record.payload.actionId === actionId);
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
      controllerRef.current = controller;
      const sequence = sequenceRef.current;
      if (!recovering) {
        replaceProjection({ ...projectionRef.current, status: 'CONNECTING', safeError: null });
      }

      const resume = async (resumeCursor: string) => {
        if (sequence !== sequenceRef.current) return;
        await connect(request, resumeCursor, true);
      };
      const armInactivity = () => {
        clearInactivity();
        inactivityRef.current = window.setTimeout(() => {
          if (sequence !== sequenceRef.current) return;
          controller.abort();
          controllerRef.current = null;
          void recoverRun(sequence, resume);
        }, 20_000);
      };
      armInactivity();

      try {
        await postAgentStream(sessionId, request, cursor, controller.signal, {
          onHeartbeat: armInactivity,
          onEvent: async (event) => {
            if (sequence !== sequenceRef.current || event.sessionId !== sessionId) return;
            armInactivity();
            const result = consumeAgentEvent(projectionRef.current, event);
            if (result.outcome === 'ignored') return;
            if (result.outcome === 'reset-required') {
              clearInactivity();
              try {
                const rebuilt = await recoverFromStreamReset(event, {
                  getRun: getAgentRun,
                  getMessages: (currentSessionId) => listAgentMessages(currentSessionId),
                });
                if (sequence !== sequenceRef.current) return;
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
          },
        });
        clearInactivity();
        controllerRef.current = null;
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
        if (sequence !== sequenceRef.current || controller.signal.aborted) return;
        controllerRef.current = null;
        const current = projectionRef.current;
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

  const submit = useCallback(
    async (content: string): Promise<boolean> => {
      const normalized = content.trim();
      if (
        !normalized ||
        normalized.length > 2000 ||
        controllerRef.current ||
        ['CONNECTING', 'STREAMING', 'RESULT_UNKNOWN'].includes(projectionRef.current.status)
      ) {
        return false;
      }
      const request: AgentStreamRequest = {
        clientRequestId: createClientRequestId(),
        content: normalized,
        context: { entry: 'workspace' },
      };
      const localItem: AgentDisplayItem = {
        key: `local:${request.clientRequestId}`,
        kind: 'user-text',
        text: normalized,
      };
      replaceProjection({
        ...projectionRef.current,
        items: [...projectionRef.current.items, localItem],
        safeError: null,
      });
      await startStream(request, projectionRef.current.lastEventId);
      return true;
    },
    [replaceProjection, startStream],
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
          setFeedback(confirmationErrorMessage(error));
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
      setFeedback('当前会话已清空');
      replaceProjection(createAgentProjection(sessionId));
      await refreshSessions();
      return true;
    } catch (error) {
      setFeedback(safeErrorMessage(error));
      return false;
    }
  }, [refreshSessions, replaceProjection, sessionId]);

  const clearAll = useCallback(async () => {
    try {
      const result = await clearAllAgentSessions();
      setFeedback(`已清空 ${result.clearedCount} 个会话，跳过 ${result.skippedCount} 个运行中会话`);
      await refreshSessions();
      return result;
    } catch (error) {
      setFeedback(safeErrorMessage(error));
      return null;
    }
  }, [refreshSessions]);

  const createNewSession = useCallback(async () => {
    try {
      const created = await createAgentSession();
      await refreshSessions();
      return created;
    } catch (error) {
      setFeedback(safeErrorMessage(error));
      return null;
    }
  }, [refreshSessions]);

  return {
    cancel,
    clearAll,
    clearCurrent,
    confirm,
    createNewSession,
    feedback,
    loadStatus,
    projection,
    refreshSessions,
    sessions,
    stopActiveStream,
    submit,
  };
}
