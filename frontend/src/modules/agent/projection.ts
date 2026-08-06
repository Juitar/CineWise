import { AGENT_EVENT_TYPES } from './types';
import type { AgentEvent, AgentMessage, AgentRunSnapshot, AgentWorkspaceStatus } from './types';
import { validateAgentCardEvent, validateAgentToolEvent } from './contract';

export type AgentDisplayKind =
  | 'assistant-text'
  | 'card-placeholder'
  | 'movie-card'
  | 'plan-card'
  | 'completed'
  | 'error'
  | 'progress'
  | 'question'
  | 'business-intent'
  | 'user-text';

export interface AgentDisplayItem {
  key: string;
  kind: AgentDisplayKind;
  text: string;
  title?: string;
  fields?: readonly { label: string; value: string }[];
}

export interface AgentProjection {
  sessionId: string;
  runId: string | null;
  planVersion: number | null;
  lastEventId: string;
  status: AgentWorkspaceStatus;
  items: readonly AgentDisplayItem[];
  safeError: string | null;
}

export type AgentConsumeResult =
  | { outcome: 'applied'; projection: AgentProjection }
  | { outcome: 'ignored'; projection: AgentProjection }
  | { outcome: 'rejected'; projection: AgentProjection }
  | { outcome: 'reset-required'; projection: AgentProjection; event: AgentEvent };

function normalizeDecimal(value: string): string {
  return value.replace(/^0+(?=\d)/, '');
}

/** 比较任意长度十进制字符串，不经过 JavaScript number。 */
export function compareDecimalStrings(left: string, right: string): number {
  const normalizedLeft = normalizeDecimal(left);
  const normalizedRight = normalizeDecimal(right);
  if (normalizedLeft.length !== normalizedRight.length) {
    return normalizedLeft.length < normalizedRight.length ? -1 : 1;
  }
  return normalizedLeft === normalizedRight ? 0 : normalizedLeft < normalizedRight ? -1 : 1;
}

export function createAgentProjection(sessionId: string): AgentProjection {
  return {
    sessionId,
    runId: null,
    planVersion: null,
    lastEventId: '0',
    status: 'IDLE',
    items: [],
    safeError: null,
  };
}

const CONFIRMATION_STATUS_TEXT: Readonly<Record<string, string>> = {
  PENDING_CONFIRMATION: '确认操作待处理（只读）',
  EXECUTING: '确认操作处理中（只读）',
  RESULT_UNKNOWN: '确认结果暂时无法确定，请等待状态恢复',
  SUCCEEDED: '确认操作已完成（只读）',
  FAILED: '确认操作未完成（只读）',
  EXPIRED: '确认操作已过期（只读）',
  REJECTED: '确认操作已拒绝（只读）',
  INVALIDATED: '确认内容已失效（只读）',
};

export function safeConfirmationStatusText(value: unknown): string | null {
  return typeof value === 'string' ? (CONFIRMATION_STATUS_TEXT[value] ?? null) : null;
}

function payloadText(payload: Readonly<Record<string, unknown>>, key: string): string | null {
  const value = payload[key];
  return typeof value === 'string' && value ? value : null;
}

function cardStatusFields(event: AgentEvent): Array<{ label: string; value: string }> {
  const source = payloadText(event.payload, 'source');
  const dataAt = payloadText(event.payload, 'dataAt');
  const expiresAt = payloadText(event.payload, 'expiresAt');
  const explicitlyExpired = event.payload.expired === true;
  const expiredByTime = expiresAt !== null && Date.parse(expiresAt) <= Date.now();
  const degraded = event.payload.degraded === true;
  const fields: Array<{ label: string; value: string }> = [];
  if (source) fields.push({ label: '来源', value: source });
  if (dataAt) fields.push({ label: '数据时间', value: dataAt });
  if (expiresAt) fields.push({ label: '有效期至', value: expiresAt });
  fields.push({ label: '状态', value: explicitlyExpired || expiredByTime ? '已过期' : '有效' });
  fields.push({ label: '数据情况', value: degraded ? '降级结果' : '正常数据' });
  if (typeof event.payload.purchaseEligible === 'boolean') {
    fields.push({
      label: '可购状态',
      value: event.payload.purchaseEligible ? '服务端标记为可购' : '当前不可购',
    });
  }
  if (Array.isArray(event.payload.missingFactors) && event.payload.missingFactors.length > 0) {
    fields.push({ label: '缺少条件', value: event.payload.missingFactors.join('、') });
  }
  return fields;
}

function candidateFields(event: AgentEvent, collection: 'movies' | 'plans') {
  const candidates = event.payload[collection] as readonly Record<string, unknown>[];
  return candidates.flatMap((candidate, index) => {
    const prefix = collection === 'movies' ? `影片 ${index + 1}` : `方案 ${index + 1}`;
    const fields: Array<{ label: string; value: string }> = [];
    const entries = [
      ['影片 ID', candidate.movieId],
      ['影院 ID', candidate.cinemaId],
      ['场次 ID', candidate.showId],
      ['价格', candidate.price],
      ['开场时间', candidate.startTime],
      ['来源', candidate.source],
    ] as const;
    entries.forEach(([label, value]) => {
      if (typeof value === 'string' && value) fields.push({ label: `${prefix} · ${label}`, value });
    });
    if (candidate.expired === true) fields.push({ label: `${prefix} · 状态`, value: '已过期' });
    if (candidate.purchaseEligible === false) {
      fields.push({ label: `${prefix} · 可购状态`, value: '当前不可购' });
    }
    return fields;
  });
}

function locationStateText(event: AgentEvent): string | null {
  if (event.payload.questionKind !== 'LOCATION_PERMISSION') return null;
  const authorization = event.payload.locationAuthorization as Record<string, unknown>;
  const state = authorization.authorizationState;
  if (state === 'GRANTED') return '已授权当前位置，仅在本次页面操作中临时使用';
  if (state === 'DENIED') {
    return authorization.deniedAction === 'USE_MANUAL_INPUT'
      ? '已拒绝位置授权，可手动输入地点'
      : '已拒绝位置授权';
  }
  if (state === 'EXPIRED') return '位置授权已过期，需要重新授权';
  return '等待位置授权';
}

function typedCard(event: AgentEvent): AgentDisplayItem {
  const key = `event:${event.eventId}`;
  const type = event.payload.type;
  if (type === 'TEXT') {
    return { key, kind: 'assistant-text', text: event.payload.text as string };
  }
  if (type === 'QUESTION') {
    const locationState = locationStateText(event);
    return {
      key,
      kind: 'question',
      title: event.payload.message as string,
      text: locationState ?? (event.payload.message as string),
      fields: locationState ? [{ label: '位置授权', value: locationState }] : undefined,
    };
  }
  if (type === 'BUSINESS_INTENT') {
    const nested = event.payload.payload as Record<string, unknown>;
    const businessRef = nested.businessRef as Record<string, unknown>;
    return {
      key,
      kind: 'business-intent',
      title: '已确认场次',
      text: '已确认场次，可选座',
      fields: [{ label: '场次 ID', value: businessRef.showId as string }],
    };
  }
  if (type === 'PROGRESS') {
    return {
      key,
      kind: 'progress',
      text: event.displayText || '正在处理',
      fields: [
        { label: '阶段', value: event.payload.stage as string },
        { label: '状态', value: event.payload.status as string },
      ],
    };
  }
  if (type === 'ERROR') {
    return { key, kind: 'error', text: event.displayText || '本次请求未完成' };
  }
  const isPlan = type === 'PLAN_CARD';
  return {
    key,
    kind: isPlan ? 'plan-card' : 'movie-card',
    title: event.payload.title as string,
    text:
      event.payload.degraded === true
        ? '当前结果为降级数据，请注意来源和有效时间'
        : '以下内容来自服务端卡片数据',
    fields: [...candidateFields(event, isPlan ? 'plans' : 'movies'), ...cardStatusFields(event)],
  };
}

function safePlaceholder(event: AgentEvent): AgentDisplayItem {
  const confirmationText = safeConfirmationStatusText(event.payload.status);
  return {
    key: `event:${event.eventId}`,
    kind: 'card-placeholder',
    text: confirmationText ?? '暂不支持此类 Agent 内容，已安全隐藏详情',
  };
}

function displayItem(event: AgentEvent): AgentDisplayItem | null {
  const key = `event:${event.eventId}`;
  if (event.eventType === 'card') {
    const confirmationText = safeConfirmationStatusText(event.payload.status);
    return confirmationText
      ? { key, kind: 'card-placeholder', text: confirmationText }
      : typedCard(event);
  }
  if (event.eventType === 'message.error' || event.eventType === 'step.failed') {
    return { key, kind: 'error', text: event.displayText || '本次请求未完成' };
  }
  if (event.eventType === 'run.complete' || event.eventType === 'message.complete') {
    return { key, kind: 'completed', text: event.displayText || '运行已完成' };
  }
  if (event.eventType === 'message.delta') {
    return { key, kind: 'assistant-text', text: event.displayText };
  }
  if (event.eventType === 'tool.complete' && event.payload.degraded === true) {
    return { key, kind: 'progress', text: `${event.displayText || '工具已完成'}，结果可能不完整` };
  }
  if (
    event.eventType === 'message.start' ||
    event.eventType === 'step.start' ||
    event.eventType === 'tool.start' ||
    event.eventType === 'tool.complete' ||
    event.eventType === 'tool.error' ||
    event.eventType === 'step.complete'
  ) {
    return { key, kind: 'progress', text: event.displayText || '正在处理' };
  }
  if (!['plan.created', 'plan.replanned'].includes(event.eventType)) return safePlaceholder(event);
  return null;
}

function nextStatus(event: AgentEvent, current: AgentWorkspaceStatus): AgentWorkspaceStatus {
  if (event.eventType === 'message.error' || event.eventType === 'step.failed') return 'FAILED';
  if (event.eventType === 'run.complete') {
    return event.payload.status === 'CANCELLED'
      ? 'CANCELLED'
      : event.payload.status === 'FAILED'
        ? 'FAILED'
        : 'COMPLETED';
  }
  if (event.eventType === 'message.complete') return 'COMPLETED';
  return current === 'CONNECTING' || current === 'IDLE' ? 'STREAMING' : current;
}

/** 应用一个已校验事件；只有返回 applied 时才推进游标。 */
export function consumeAgentEvent(
  projection: AgentProjection,
  event: AgentEvent,
): AgentConsumeResult {
  if (event.sessionId !== projection.sessionId) return { outcome: 'ignored', projection };
  if (projection.runId !== null && event.runId !== projection.runId) {
    return { outcome: 'ignored', projection };
  }
  if (compareDecimalStrings(event.eventId, projection.lastEventId) <= 0) {
    return { outcome: 'ignored', projection };
  }
  if (
    event.planVersion !== null &&
    projection.planVersion !== null &&
    event.planVersion < projection.planVersion
  ) {
    return { outcome: 'ignored', projection };
  }
  if (event.eventType === 'stream.reset') {
    return { outcome: 'reset-required', projection, event };
  }

  const cardValidation = event.eventType === 'card' ? validateAgentCardEvent(event) : null;
  if (cardValidation?.decision === 'reject') {
    return {
      outcome: 'rejected',
      projection: {
        ...projection,
        safeError: '收到的 Agent 内容不完整，已保留当前结果',
      },
    };
  }

  if (
    event.eventType === 'tool.start' ||
    event.eventType === 'tool.complete' ||
    event.eventType === 'tool.error'
  ) {
    if (!validateAgentToolEvent(event)) {
      return {
        outcome: 'rejected',
        projection: { ...projection, safeError: '收到的工具进度内容不完整，已保留当前结果' },
      };
    }
  }

  const unknownEvent = !AGENT_EVENT_TYPES.some((type) => type === event.eventType);
  if (unknownEvent) return { outcome: 'ignored', projection };
  const item =
    cardValidation?.decision === 'safe-text' || unknownEvent
      ? safePlaceholder(event)
      : displayItem(event);
  const failed = event.eventType === 'message.error' || event.eventType === 'step.failed';
  return {
    outcome: 'applied',
    projection: {
      ...projection,
      runId: projection.runId ?? event.runId,
      planVersion:
        event.planVersion !== null &&
        (projection.planVersion === null || event.planVersion > projection.planVersion)
          ? event.planVersion
          : projection.planVersion,
      lastEventId: event.eventId,
      status: nextStatus(event, projection.status),
      items: item ? [...projection.items, item] : projection.items,
      safeError: failed ? (item?.text ?? '本次请求未完成') : projection.safeError,
    },
  };
}

function itemFromHistory(message: AgentMessage): AgentDisplayItem {
  const type = message.type.toUpperCase();
  const confirmationText = safeConfirmationStatusText(message.payload.status);
  if (confirmationText) {
    return {
      key: `message:${message.messageId}`,
      kind: 'card-placeholder',
      text: confirmationText,
    };
  }
  if (type === 'MOVIE_CARD' || type === 'PLAN_CARD') {
    return {
      key: `message:${message.messageId}`,
      kind: 'card-placeholder',
      text: '卡片数据暂不完整',
    };
  }
  if (type === 'QUESTION') {
    return { key: `message:${message.messageId}`, kind: 'question', text: message.text };
  }
  if (type === 'ERROR') {
    return {
      key: `message:${message.messageId}`,
      kind: 'error',
      text: message.text || '请求未完成',
    };
  }
  return {
    key: `message:${message.messageId}`,
    kind: message.role === 'USER' ? 'user-text' : 'assistant-text',
    text: message.text,
  };
}

export function buildProjectionFromHistory(
  sessionId: string,
  messages: readonly AgentMessage[],
): AgentProjection {
  return { ...createAgentProjection(sessionId), items: messages.map(itemFromHistory) };
}

/** 使用运行快照和会话历史整体重建，不把快照事件重复追加到已有消息。 */
export function buildProjectionFromSnapshot(
  snapshot: AgentRunSnapshot,
  history: readonly AgentMessage[],
): AgentProjection {
  const historyProjection = buildProjectionFromHistory(snapshot.sessionId, history);
  const restoredCards = snapshot.events
    .filter(
      (event) =>
        event.sessionId === snapshot.sessionId &&
        event.runId === snapshot.runId &&
        event.eventType === 'card' &&
        validateAgentCardEvent(event).decision === 'render' &&
        (event.payload.type === 'MOVIE_CARD' || event.payload.type === 'PLAN_CARD'),
    )
    .map(typedCard);
  const historyItems =
    restoredCards.length === 0
      ? historyProjection.items
      : historyProjection.items.filter((_, index) => {
          const type = history[index]?.type.toUpperCase();
          return type !== 'MOVIE_CARD' && type !== 'PLAN_CARD';
        });
  const snapshotItems: AgentDisplayItem[] =
    history.length > 0
      ? []
      : snapshot.messages
          .filter(
            (message) =>
              restoredCards.length === 0 ||
              (message.type !== 'MOVIE_CARD' && message.type !== 'PLAN_CARD'),
          )
          .map((message) => ({
            key: `run-message:${message.messageId}`,
            kind:
              message.type === 'MOVIE_CARD' || message.type === 'PLAN_CARD'
                ? 'card-placeholder'
                : message.role === 'USER'
                  ? 'user-text'
                  : 'assistant-text',
            text:
              message.type === 'MOVIE_CARD' || message.type === 'PLAN_CARD'
                ? '卡片数据暂不完整'
                : message.text,
          }));
  const stepItems: AgentDisplayItem[] = snapshot.steps.map((step) => ({
    key: `run-step:${step.nodeId}`,
    kind: step.status === 'FAILED' ? 'error' : step.status === 'SUCCESS' ? 'completed' : 'progress',
    text:
      step.status === 'FAILED'
        ? '有步骤未完成'
        : step.status === 'SUCCESS'
          ? '步骤已完成'
          : '步骤处理中',
  }));
  return {
    ...historyProjection,
    runId: snapshot.runId,
    planVersion: snapshot.planVersion,
    lastEventId: snapshot.lastEventId,
    status: snapshot.status === 'RUNNING' ? 'STREAMING' : snapshot.status,
    items: [...historyItems, ...snapshotItems, ...restoredCards, ...stepItems],
  };
}
