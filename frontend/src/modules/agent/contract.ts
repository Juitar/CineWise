import type {
  AgentEvent,
  AgentActionConfirmationResult,
  AgentConfirmationStatus,
  AgentCardPayloadType,
  AgentMessage,
  AgentMessagePage,
  AgentRunCancelResult,
  AgentRunMessage,
  AgentRunSnapshot,
  AgentRunStatus,
  AgentRunStep,
  AgentSession,
  AgentSessionBulkClearResult,
  AgentSessionClearResult,
  AgentSessionPage,
} from './types';

const RUN_STATUSES = new Set<AgentRunStatus>(['RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED']);
const CARD_PAYLOAD_TYPES = new Set<AgentCardPayloadType>([
  'TEXT',
  'QUESTION',
  'MOVIE_CARD',
  'PLAN_CARD',
  'TRAVEL_ADVICE_CARD',
  'BUSINESS_INTENT',
  'PROGRESS',
  'ERROR',
]);
const LOCATION_AUTHORIZATION_STATES = new Set(['NOT_REQUESTED', 'GRANTED', 'DENIED', 'EXPIRED']);
const BUSINESS_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const DECIMAL_ID_PATTERN = /^(0|[1-9]\d*)$/;
const POSITIVE_JAVA_LONG_PATTERN = /^[1-9]\d*$/;
const JAVA_LONG_MAX = '9223372036854775807';
const CONFIRMATION_STATUSES = new Set<AgentConfirmationStatus>([
  'PENDING_CONFIRMATION',
  'EXECUTING',
  'RESULT_UNKNOWN',
  'SUCCEEDED',
  'FAILED',
  'EXPIRED',
  'REJECTED',
  'INVALIDATED',
]);

export class AgentContractError extends Error {
  constructor(message = 'Agent 服务返回的数据格式不正确') {
    super(message);
    this.name = 'AgentContractError';
  }
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function record(value: unknown): Record<string, unknown> {
  if (!isRecord(value)) throw new AgentContractError();
  return value;
}

function text(value: unknown, allowEmpty = false): string {
  if (typeof value !== 'string' || (!allowEmpty && value.length === 0)) {
    throw new AgentContractError();
  }
  return value;
}

function businessId(value: unknown): string {
  const valueText = text(value);
  if (!BUSINESS_ID_PATTERN.test(valueText)) throw new AgentContractError('Agent ID 格式不正确');
  return valueText;
}

export function decimalId(value: unknown): string {
  const valueText = text(value);
  if (!DECIMAL_ID_PATTERN.test(valueText)) throw new AgentContractError('Agent 游标格式不正确');
  return valueText;
}

function eventId(value: unknown): string {
  const valueText = decimalId(value);
  if (valueText === '0') throw new AgentContractError('Agent 事件 ID 格式不正确');
  return valueText;
}

function nullableText(value: unknown): string | null {
  return value === null ? null : text(value, true);
}

function optionalNullableText(value: Record<string, unknown>, key: string): string | null {
  return key in value ? nullableText(value[key]) : null;
}

function dateText(value: unknown): string {
  const valueText = text(value);
  if (Number.isNaN(Date.parse(valueText))) throw new AgentContractError();
  return valueText;
}

function optionalNullableDate(value: Record<string, unknown>, key: string): string | null {
  if (!(key in value) || value[key] === null) return null;
  return dateText(value[key]);
}

function nonNegativeInteger(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw new AgentContractError();
  }
  return value;
}

function nullablePlanVersion(value: unknown): number | null {
  return value === null ? null : nonNegativeInteger(value);
}

function array(value: unknown): readonly unknown[] {
  if (!Array.isArray(value)) throw new AgentContractError();
  return value;
}

function runStatus(value: unknown): AgentRunStatus {
  const current = text(value) as AgentRunStatus;
  if (!RUN_STATUSES.has(current)) throw new AgentContractError();
  return current;
}

function page<T>(value: unknown, parseItem: (item: unknown) => T) {
  const current = record(value);
  return {
    total: nonNegativeInteger(current.total),
    page: nonNegativeInteger(current.page),
    size: nonNegativeInteger(current.size),
    records: array(current.records).map(parseItem),
  };
}

export function parseAgentSession(value: unknown): AgentSession {
  const current = record(value);
  return {
    sessionId: businessId(current.sessionId),
    summary: nullableText(current.summary),
    status: text(current.status),
    createdAt: dateText(current.createdAt),
    updatedAt: dateText(current.updatedAt),
  };
}

export function parseAgentSessionPage(value: unknown): AgentSessionPage {
  return page(value, parseAgentSession);
}

export function parseAgentMessage(value: unknown): AgentMessage {
  const current = record(value);
  return {
    messageId: businessId(current.messageId),
    role: text(current.role),
    type: text(current.type),
    text: text(current.text, true),
    runId: businessId(current.runId),
    payload: record(current.payload),
    status: text(current.status),
    completedAt: optionalNullableDate(current, 'completedAt'),
    createdAt: dateText(current.createdAt),
  };
}

export function parseAgentMessagePage(value: unknown): AgentMessagePage {
  return page(value, parseAgentMessage);
}

/** 校验未知 SSE JSON；未知顶层字段不会进入页面投影。 */
export function parseAgentEvent(value: unknown): AgentEvent {
  const current = record(value);
  const payload = record(current.payload);
  if (Object.keys(payload).length === 0) {
    throw new AgentContractError('Agent 事件 payload 不能为空');
  }
  const currentEventType = text(current.eventType);
  if (currentEventType === 'stream.reset') decimalId(payload.watermark);
  if (
    currentEventType === 'run.complete' &&
    !['CANCELLED', 'COMPLETED', 'FAILED'].includes(text(payload.status))
  ) {
    throw new AgentContractError('Agent 运行终态不正确');
  }
  return {
    eventId: eventId(current.eventId),
    sessionId: businessId(current.sessionId),
    runId: businessId(current.runId),
    planId: optionalNullableText(current, 'planId'),
    planVersion: nullablePlanVersion(current.planVersion),
    nodeId: nullableText(current.nodeId),
    eventType: currentEventType,
    displayText: text(current.displayText, true),
    payload,
    occurredAt: optionalNullableDate(current, 'occurredAt'),
  };
}

export type AgentCardValidation =
  | { decision: 'render'; payloadType: AgentCardPayloadType }
  | { decision: 'safe-text' }
  | { decision: 'reject' };

function boolean(value: unknown): boolean {
  if (typeof value !== 'boolean') throw new AgentContractError();
  return value;
}

function optionalBoolean(current: Record<string, unknown>, key: string): void {
  if (key in current) boolean(current[key]);
}

function optionalText(current: Record<string, unknown>, key: string): void {
  if (key in current && current[key] !== null) text(current[key]);
}

function optionalBusinessId(current: Record<string, unknown>, key: string): void {
  if (key in current && current[key] !== null) businessId(current[key]);
}

function textArray(value: unknown): void {
  array(value).forEach((item) => text(item));
}

function nullableNumber(value: unknown): void {
  if (value !== null && (typeof value !== 'number' || !Number.isFinite(value))) {
    throw new AgentContractError();
  }
}

function nullableNonNegativeInteger(value: unknown): void {
  if (value !== null) nonNegativeInteger(value);
}

function validateRecommendationItem(value: unknown, type: 'MOVIE_CARD' | 'PLAN_CARD'): void {
  const item = record(value);
  businessId(item.movieId);
  if (type === 'PLAN_CARD') {
    text(item.planType);
    text(item.movieName);
    businessId(item.cinemaId);
    text(item.cinemaName);
    businessId(item.showId);
    text(item.price);
    text(item.currency);
    dateText(item.startTime);
    if (item.rating !== null) text(item.rating);
    nullableNumber(item.score);
    textArray(item.reasons);
    text(item.source);
    dateText(item.dataAt);
    dateText(item.expiresAt);
    boolean(item.expired);
    boolean(item.purchaseEligible);
    nullableNonNegativeInteger(item.distanceMeters);
    return;
  }
  optionalText(item, 'title');
  optionalBusinessId(item, 'cinemaId');
  optionalBusinessId(item, 'showId');
  optionalText(item, 'price');
  if ('startTime' in item && item.startTime !== null) dateText(item.startTime);
  optionalText(item, 'source');
  optionalBoolean(item, 'expired');
  optionalBoolean(item, 'purchaseEligible');
}

function validateQuestion(payload: Record<string, unknown>): void {
  businessId(payload.questionId);
  text(payload.questionKind);
  text(payload.message);
  array(payload.options).forEach((option) => {
    const current = record(option);
    businessId(current.optionId);
    text(current.label);
    text(current.value);
  });
  boolean(payload.allowFreeText);
  boolean(payload.requiresConfirmation);
  dateText(payload.expiresAt);
  const input = record(payload.input);
  text(input.name);
  text(input.type);
  if (payload.questionKind === 'LOCATION_PERMISSION') {
    const authorization = record(payload.locationAuthorization);
    if (authorization.permission !== 'DEVICE_LOCATION') throw new AgentContractError();
    if (
      typeof authorization.authorizationState !== 'string' ||
      !LOCATION_AUTHORIZATION_STATES.has(authorization.authorizationState)
    ) {
      throw new AgentContractError();
    }
    if (authorization.purpose !== 'ROUTE_PLANNING') throw new AgentContractError();
    boolean(authorization.resubmittable);
    text(authorization.deniedAction);
    text(authorization.expiredAction);
  }
}

function validateRecommendation(
  payload: Record<string, unknown>,
  type: 'MOVIE_CARD' | 'PLAN_CARD',
): void {
  text(payload.title);
  const candidates = array(type === 'MOVIE_CARD' ? payload.movies : payload.plans);
  candidates.forEach((item) => validateRecommendationItem(item, type));
  text(payload.source);
  dateText(payload.dataAt);
  dateText(payload.expiresAt);
  boolean(payload.degraded);
  if (type === 'PLAN_CARD') {
    text(payload.schemaVersion);
    text(payload.algorithmVersion);
    textArray(payload.missingFactors);
    boolean(payload.usedProfile);
    boolean(payload.expired);
    if (payload.relaxationSuggestion !== null) {
      const suggestion = record(payload.relaxationSuggestion);
      text(suggestion.factor);
      text(suggestion.message);
    }
  }
  optionalBoolean(payload, 'expired');
  optionalBoolean(payload, 'purchaseEligible');
  if ('missingFactors' in payload) textArray(payload.missingFactors);
  optionalText(payload, 'fallbackType');
}
function validateConfirmationCard(payload: Record<string, unknown>): void {
  businessId(payload.actionId);
  text(payload.actionType);
  text(payload.title);
  textArray(payload.displayLines);
  dateText(payload.expiresAt);
  if (
    typeof payload.status !== 'string' ||
    !CONFIRMATION_STATUSES.has(payload.status as AgentConfirmationStatus)
  )
    throw new AgentContractError();
}

function validateBusinessIntent(payload: Record<string, unknown>): void {
  if (payload.type !== 'BUSINESS_INTENT') throw new AgentContractError();
  const nested = record(payload.payload);
  if (nested.intent !== 'SELECT_SEATS') throw new AgentContractError();
  const businessRef = record(nested.businessRef);
  for (const key of ['showId', 'movieId', 'cinemaId'] as const) {
    const id = text(businessRef[key]);
    if (
      !POSITIVE_JAVA_LONG_PATTERN.test(id) ||
      id.length > JAVA_LONG_MAX.length ||
      (id.length === JAVA_LONG_MAX.length && id > JAVA_LONG_MAX)
    ) {
      throw new AgentContractError();
    }
  }
}

function exactKeys(value: Record<string, unknown>, keys: readonly string[]): void {
  if (Object.keys(value).some((key) => !keys.includes(key))) throw new AgentContractError();
}

function travelPositiveId(value: unknown): void {
  const id = text(value);
  if (
    !POSITIVE_JAVA_LONG_PATTERN.test(id) ||
    id.length > JAVA_LONG_MAX.length ||
    (id.length === JAVA_LONG_MAX.length && id > JAVA_LONG_MAX)
  )
    throw new AgentContractError();
}

function validateTravelAdviceCard(payload: Record<string, unknown>): void {
  exactKeys(payload, [
    'type',
    'taskId',
    'taskStatus',
    'available',
    'weather',
    'advice',
    'source',
    'degraded',
    'fallbackType',
    'dataAt',
    'expiresAt',
    'expired',
  ]);
  travelPositiveId(payload.taskId);
  text(payload.taskStatus);
  const available = boolean(payload.available);
  const weather = payload.weather;
  if (weather !== null) {
    const current = record(weather);
    exactKeys(current, ['area', 'condition', 'risk']);
    nullableText(current.area);
    nullableText(current.condition);
    nullableText(current.risk);
  }
  array(payload.advice).forEach((item) => {
    const current = record(item);
    exactKeys(current, ['type', 'text']);
    text(current.type);
    text(current.text);
  });
  const source = nullableText(payload.source);
  const degraded = boolean(payload.degraded);
  const fallbackType = nullableText(payload.fallbackType);
  const dataAt = payload.dataAt === null ? null : dateText(payload.dataAt);
  const expiresAt = payload.expiresAt === null ? null : dateText(payload.expiresAt);
  boolean(payload.expired);
  if (available && source === null) throw new AgentContractError();
  if (
    !available &&
    (weather !== null ||
      array(payload.advice).length > 0 ||
      degraded ||
      fallbackType !== null ||
      dataAt !== null ||
      expiresAt !== null)
  )
    throw new AgentContractError();
  if (degraded !== (fallbackType !== null)) throw new AgentContractError();
}

function validateToolPayload(event: AgentEvent): void {
  const payload = event.payload;
  text(payload.toolName);
  text(payload.displayText);
  if (event.eventType === 'tool.complete') {
    boolean(payload.degraded);
    optionalText(payload, 'fallbackType');
    if ('dataAt' in payload && payload.dataAt !== null) dateText(payload.dataAt);
    return;
  }
  if (event.eventType === 'tool.start') return;
  const errorCode = payload.errorCode;
  if (
    !(typeof errorCode === 'string' && errorCode.length > 0) &&
    !(typeof errorCode === 'number' && Number.isSafeInteger(errorCode) && errorCode >= 0)
  ) {
    throw new AgentContractError();
  }
  boolean(payload.retryable);
  boolean(payload.replanSuggested);
}

/** 按 db7b622 的白名单校验卡片；未知类型安全降级，已知类型不完整则拒绝。 */
export function validateAgentCardEvent(event: AgentEvent): AgentCardValidation {
  if (event.eventType !== 'card') return { decision: 'safe-text' };
  const type = event.payload.type;
  if (typeof type !== 'string' || !CARD_PAYLOAD_TYPES.has(type as AgentCardPayloadType)) {
    return { decision: 'safe-text' };
  }
  try {
    if ('actionId' in event.payload) {
      validateConfirmationCard(event.payload as Record<string, unknown>);
      return { decision: 'render', payloadType: type as AgentCardPayloadType };
    }
    if ((type === 'MOVIE_CARD' || type === 'PLAN_CARD') && (!event.planId || !event.planVersion)) {
      throw new AgentContractError();
    }
    if (type === 'MOVIE_CARD' || type === 'PLAN_CARD') businessId(event.planId);
    if (type === 'TEXT') text(event.payload.text);
    if (type === 'QUESTION') validateQuestion(event.payload as Record<string, unknown>);
    if (type === 'BUSINESS_INTENT') {
      if (!event.nodeId || !event.planId || event.planVersion === null)
        throw new AgentContractError();
      validateBusinessIntent(event.payload as Record<string, unknown>);
    }
    if (type === 'TRAVEL_ADVICE_CARD')
      validateTravelAdviceCard(event.payload as Record<string, unknown>);
    if (type === 'MOVIE_CARD' || type === 'PLAN_CARD') {
      validateRecommendation(event.payload as Record<string, unknown>, type);
    }
    if (type === 'PROGRESS') {
      text(event.payload.stage);
      text(event.payload.status);
    }
    if (type === 'ERROR') {
      text(event.payload.code);
      text(event.payload.message);
      boolean(event.payload.retryable);
    }
    return { decision: 'render', payloadType: type as AgentCardPayloadType };
  } catch {
    return { decision: 'reject' };
  }
}

export function parseAgentActionConfirmationResult(value: unknown): AgentActionConfirmationResult {
  const current = record(value);
  const status = text(current.status) as AgentConfirmationStatus;
  if (!CONFIRMATION_STATUSES.has(status)) throw new AgentContractError();
  return {
    actionId: businessId(current.actionId),
    runId: businessId(current.runId),
    planVersion: nonNegativeInteger(current.planVersion),
    status,
    updatedAt: dateText(current.updatedAt),
  };
}

export function validateAgentToolEvent(event: AgentEvent): boolean {
  if (!['tool.start', 'tool.complete', 'tool.error'].includes(event.eventType)) return false;
  try {
    validateToolPayload(event);
    return true;
  } catch {
    return false;
  }
}

function parseRunMessage(value: unknown): AgentRunMessage {
  const current = record(value);
  return {
    messageId: businessId(current.messageId),
    role: text(current.role),
    type: text(current.type),
    text: text(current.text, true),
    completedAt: optionalNullableDate(current, 'completedAt'),
  };
}

function parseRunStep(value: unknown): AgentRunStep {
  const current = record(value);
  if (typeof current.autoSkipped !== 'boolean') throw new AgentContractError();
  return {
    nodeId: businessId(current.nodeId),
    nodeType: text(current.nodeType),
    status: text(current.status),
    attemptCount: nonNegativeInteger(current.attemptCount),
    autoSkipped: current.autoSkipped,
    recoveryHint: optionalNullableText(current, 'recoveryHint'),
  };
}

export function parseAgentRunSnapshot(value: unknown): AgentRunSnapshot {
  const current = record(value);
  return {
    runId: businessId(current.runId),
    sessionId: businessId(current.sessionId),
    status: runStatus(current.status),
    planId: optionalNullableText(current, 'planId'),
    planVersion: nullablePlanVersion(current.planVersion),
    startedAt: optionalNullableDate(current, 'startedAt'),
    finishedAt: optionalNullableDate(current, 'finishedAt'),
    lastEventId: decimalId(current.lastEventId),
    messages: array(current.messages).map(parseRunMessage),
    steps: array(current.steps).map(parseRunStep),
    events: array(current.events).map(parseAgentEvent),
  };
}

export function parseSessionClearResult(value: unknown): AgentSessionClearResult {
  const current = record(value);
  if (typeof current.cleared !== 'boolean') throw new AgentContractError();
  return { sessionId: businessId(current.sessionId), cleared: current.cleared };
}

export function parseSessionBulkClearResult(value: unknown): AgentSessionBulkClearResult {
  const current = record(value);
  return {
    clearedCount: nonNegativeInteger(current.clearedCount),
    skippedCount: nonNegativeInteger(current.skippedCount),
  };
}

export function parseRunCancelResult(value: unknown): AgentRunCancelResult {
  const current = record(value);
  return {
    runId: businessId(current.runId),
    status: runStatus(current.status),
    finishedAt: optionalNullableDate(current, 'finishedAt'),
  };
}
