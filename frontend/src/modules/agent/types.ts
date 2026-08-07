import type { PageResult } from '../../shared/types/api';

export const AGENT_EVENT_TYPES = [
  'message.start',
  'message.delta',
  'plan.created',
  'plan.replanned',
  'step.start',
  'step.complete',
  'step.failed',
  'tool.start',
  'tool.complete',
  'tool.error',
  'card',
  'message.complete',
  'message.error',
  'run.complete',
  'stream.reset',
] as const;

export type AgentEventType = (typeof AGENT_EVENT_TYPES)[number];
export type AgentWorkspaceStatus =
  'IDLE' | 'CONNECTING' | 'STREAMING' | 'COMPLETED' | 'FAILED' | 'RESULT_UNKNOWN' | 'CANCELLED';
export type AgentRunStatus = 'CANCELLED' | 'COMPLETED' | 'FAILED' | 'RUNNING';
export type AgentConfirmationStatus =
  | 'PENDING_CONFIRMATION'
  | 'EXECUTING'
  | 'RESULT_UNKNOWN'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'EXPIRED'
  | 'REJECTED'
  | 'INVALIDATED';
export interface AgentActionConfirmationResult {
  actionId: string;
  runId: string;
  planVersion: number;
  status: AgentConfirmationStatus;
  updatedAt: string;
}

export interface AgentSession {
  sessionId: string;
  summary: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface AgentMessage {
  messageId: string;
  role: string;
  type: string;
  text: string;
  runId: string;
  payload: Readonly<Record<string, unknown>>;
  status: string;
  completedAt: string | null;
  createdAt: string;
}

export interface AgentEvent {
  eventId: string;
  sessionId: string;
  runId: string;
  planId: string | null;
  planVersion: number | null;
  nodeId: string | null;
  eventType: string;
  displayText: string;
  payload: Readonly<Record<string, unknown>>;
  occurredAt: string | null;
}

export type AgentCardPayloadType =
  | 'TEXT'
  | 'QUESTION'
  | 'MOVIE_CARD'
  | 'PLAN_CARD'
  | 'TRAVEL_ADVICE_CARD'
  | 'BUSINESS_INTENT'
  | 'PROGRESS'
  | 'ERROR';

export interface TravelAdviceWeatherSummary {
  area: string | null;
  condition: string | null;
  risk: string | null;
}

export interface TravelAdviceItem {
  type: string;
  text: string;
}

export interface TravelAdviceCardPayload {
  type: 'TRAVEL_ADVICE_CARD';
  taskId: string;
  taskStatus: string;
  available: boolean;
  weather: TravelAdviceWeatherSummary | null;
  advice: readonly TravelAdviceItem[];
  source: string | null;
  degraded: boolean;
  fallbackType: string | null;
  dataAt: string | null;
  expiresAt: string | null;
  expired: boolean;
}

export type BusinessIntent =
  | 'BROWSE_MOVIES'
  | 'BROWSE_CINEMAS'
  | 'VIEW_MOVIE'
  | 'VIEW_SHOWS'
  | 'SELECT_SEATS'
  | 'REVIEW_ORDER'
  | 'VIEW_ORDER'
  | 'REQUEST_REFUND'
  | 'VIEW_TRAVEL_ADVICE';

export interface ToolStartPayload {
  toolName: string;
  displayText: string;
}

export interface ToolCompletePayload extends ToolStartPayload {
  degraded: boolean;
  fallbackType?: string;
  dataAt?: string;
}

export interface ToolErrorPayload extends ToolStartPayload {
  errorCode: string | number;
  retryable: boolean;
  replanSuggested: boolean;
}

export interface BusinessIntentCardPayload {
  type: 'BUSINESS_INTENT';
  payload: {
    intent: BusinessIntent;
    businessRef: { showId: string; movieId: string; cinemaId: string };
  };
}

export interface AgentRunMessage {
  messageId: string;
  role: string;
  type: string;
  text: string;
  completedAt: string | null;
}

export interface AgentRunStep {
  nodeId: string;
  nodeType: string;
  status: string;
  attemptCount: number;
  autoSkipped: boolean;
  recoveryHint: string | null;
}

export interface AgentRunSnapshot {
  runId: string;
  sessionId: string;
  status: AgentRunStatus;
  planId: string | null;
  planVersion: number | null;
  startedAt: string | null;
  finishedAt: string | null;
  lastEventId: string;
  messages: readonly AgentRunMessage[];
  steps: readonly AgentRunStep[];
  events: readonly AgentEvent[];
}

export interface AgentRunCancelResult {
  runId: string;
  status: AgentRunStatus;
  finishedAt: string | null;
}

export interface AgentSessionClearResult {
  sessionId: string;
  cleared: boolean;
}

export interface AgentSessionBulkClearResult {
  clearedCount: number;
  skippedCount: number;
}

export type AgentSessionPage = PageResult<AgentSession>;
export type AgentMessagePage = PageResult<AgentMessage>;

export interface AgentStreamRequest {
  clientRequestId: string;
  content: string;
  context: { entry: string };
}
