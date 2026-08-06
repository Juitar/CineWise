import { ApiError } from '../../shared/api/ApiError';
import {
  clearCsrfToken,
  getCsrfRequestHeaders,
  handleUnauthorizedResponse,
} from '../../shared/api/client';
import { AgentContractError, isRecord, parseAgentEvent } from './contract';
import type { AgentEvent, AgentStreamRequest } from './types';

export interface ParsedSseMessage {
  id: string | null;
  event: string | null;
  data: string;
}

export interface AgentStreamHandlers {
  onEvent(event: AgentEvent): Promise<void> | void;
  onHeartbeat(): void;
}

function dispatchBlock(
  block: string,
  onMessage: (message: ParsedSseMessage) => void,
  onHeartbeat: () => void,
) {
  if (!block) return;
  let id: string | null = null;
  let event: string | null = null;
  const data: string[] = [];
  let onlyComments = true;

  block.split('\n').forEach((line) => {
    if (line.startsWith(':')) return;
    onlyComments = false;
    const separator = line.indexOf(':');
    const field = separator < 0 ? line : line.slice(0, separator);
    let value = separator < 0 ? '' : line.slice(separator + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'id') id = value;
    if (field === 'event') event = value;
    if (field === 'data') data.push(value);
  });

  if (onlyComments) {
    onHeartbeat();
    return;
  }
  if (data.length > 0) onMessage({ id, event, data: data.join('\n') });
}

/** 逐块解析 SSE；支持 CRLF、跨块事件、多事件同块和无 ID 注释心跳。 */
export async function parseSseStream(
  stream: ReadableStream<Uint8Array>,
  onMessage: (message: ParsedSseMessage) => Promise<void> | void,
  onHeartbeat: () => void,
): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    while (true) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done }).replace(/\r\n?/g, '\n');
      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        let pending: ParsedSseMessage | null = null;
        dispatchBlock(block, (message) => (pending = message), onHeartbeat);
        if (pending) await onMessage(pending);
        boundary = buffer.indexOf('\n\n');
      }
      if (done) break;
    }
    if (buffer.trim()) {
      let pending: ParsedSseMessage | null = null;
      dispatchBlock(buffer, (message) => (pending = message), onHeartbeat);
      if (pending) await onMessage(pending);
    }
  } finally {
    reader.releaseLock();
  }
}

async function safeHttpError(response: Response): Promise<ApiError> {
  let code: number | undefined;
  try {
    const payload: unknown = await response.json();
    if (isRecord(payload) && typeof payload.code === 'number') code = payload.code;
  } catch {
    // 错误正文不可识别时只使用固定文案，不展示服务端原文。
  }
  if (response.status === 401 || code === 201006) await handleUnauthorizedResponse();
  if (response.status === 403 && code === 201009) clearCsrfToken();
  return new ApiError('Agent 连接未成功', {
    kind: 'HTTP',
    code,
    status: response.status,
    traceId: response.headers.get('X-Trace-Id') ?? undefined,
  });
}

/** 发送一次 POST SSE；函数不会自动重试消息、取消或其他写操作。 */
export async function postAgentStream(
  sessionId: string,
  request: AgentStreamRequest,
  lastEventId: string | null,
  signal: AbortSignal,
  handlers: AgentStreamHandlers,
): Promise<void> {
  const headers = await getCsrfRequestHeaders();
  headers.set('Accept', 'text/event-stream');
  headers.set('Content-Type', 'application/json');
  if (lastEventId && lastEventId !== '0') headers.set('Last-Event-ID', lastEventId);

  let response: Response;
  try {
    response = await fetch(
      `/api/v1/agent/sessions/${encodeURIComponent(sessionId)}/messages/stream`,
      {
        method: 'POST',
        credentials: 'include',
        headers,
        body: JSON.stringify(request),
        signal,
      },
    );
    // 服务端成功建立 SSE 后可能更新 CSRF Cookie；下一次请求重新获取匹配的新 Header。
    // 非 2xx 响应必须先交给 safeHttpError，403/201007 不能清掉当前 Token。
  } catch (error) {
    if (signal.aborted) throw new ApiError('Agent 请求已取消', { kind: 'CANCELLED' });
    throw new ApiError('Agent 网络连接失败', { kind: 'NETWORK', isResultUnknown: true });
  }

  if (response.ok) clearCsrfToken();
  if (!response.ok) throw await safeHttpError(response);
  if (!response.body) {
    throw new ApiError('Agent 服务没有返回事件流', { kind: 'INVALID_RESPONSE' });
  }
  await parseSseStream(
    response.body,
    async (message) => {
      let value: unknown;
      try {
        value = JSON.parse(message.data);
      } catch {
        throw new AgentContractError('Agent 事件 JSON 不正确');
      }
      const event = parseAgentEvent(value);
      if (
        (message.id !== null && message.id !== event.eventId) ||
        (message.event !== null && message.event !== event.eventType)
      ) {
        throw new AgentContractError('Agent SSE 字段与事件内容不一致');
      }
      await handlers.onEvent(event);
    },
    handlers.onHeartbeat,
  );
}
