import type { ApiResult } from '../types/api';
import { ApiError } from './ApiError';

type QueryPrimitive = boolean | number | string;
type QueryValue = QueryPrimitive | null | readonly QueryPrimitive[] | undefined;

export interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  query?: Record<string, QueryValue>;
  timeoutMs?: number;
}

const DEFAULT_TIMEOUT_MS = 10_000;
const READ_METHODS = new Set(['GET', 'HEAD']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isApiResult(value: unknown): value is ApiResult<unknown> {
  return (
    isRecord(value) &&
    typeof value.code === 'number' &&
    typeof value.message === 'string' &&
    'data' in value &&
    typeof value.traceId === 'string'
  );
}

function isAbortError(error: unknown): boolean {
  return isRecord(error) && error.name === 'AbortError';
}

function appendQueryValue(searchParams: URLSearchParams, key: string, value: QueryValue): void {
  if (value === null || value === undefined) {
    return;
  }

  if (Array.isArray(value)) {
    value.forEach((item) => searchParams.append(key, String(item)));
    return;
  }

  searchParams.append(key, String(value));
}

function buildApiUrl(path: string, query?: Record<string, QueryValue>): string {
  if (!path.startsWith('/api/')) {
    throw new Error(`普通 REST 请求必须使用 /api/ 路径，当前路径为 ${path}`);
  }

  const searchParams = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    appendQueryValue(searchParams, key, value);
  });

  const queryString = searchParams.toString();
  return queryString.length > 0 ? `${path}?${queryString}` : path;
}

async function readApiResult(response: Response): Promise<ApiResult<unknown>> {
  const traceId = response.headers.get('X-Trace-Id') ?? undefined;
  let payload: unknown;

  try {
    payload = await response.json();
  } catch {
    throw new ApiError('服务返回了无法识别的数据', {
      kind: 'INVALID_RESPONSE',
      status: response.status,
      traceId,
    });
  }

  if (!isApiResult(payload)) {
    throw new ApiError('服务返回的数据格式不正确', {
      kind: 'INVALID_RESPONSE',
      status: response.status,
      traceId,
    });
  }

  return payload;
}

/**
 * 发送普通 REST 请求并解包后端统一响应。
 *
 * 该函数始终携带 HttpOnly Cookie，不读取或保存 Token，也不会自动重试写操作。
 * 写请求超时或断网时会标记 isResultUnknown，调用模块必须使用原业务 ID 查询结果。
 */
export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { body, query, timeoutMs = DEFAULT_TIMEOUT_MS, ...requestInit } = options;
  const method = (requestInit.method ?? 'GET').toUpperCase();
  const isWriteRequest = !READ_METHODS.has(method);
  const controller = new AbortController();
  let didTimeout = false;

  const handleExternalAbort = () => controller.abort();
  if (requestInit.signal?.aborted) {
    controller.abort();
  } else {
    requestInit.signal?.addEventListener('abort', handleExternalAbort, { once: true });
  }

  const timeoutId = window.setTimeout(() => {
    didTimeout = true;
    controller.abort();
  }, timeoutMs);

  const headers = new Headers(requestInit.headers);
  headers.set('Accept', 'application/json');
  if (body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  const url = buildApiUrl(path, query);

  try {
    const response = await fetch(url, {
      ...requestInit,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'include',
      headers,
      method,
      signal: controller.signal,
    });
    const result = await readApiResult(response);

    if (!response.ok) {
      throw new ApiError('请求未成功，请稍后重试', {
        kind: 'HTTP',
        code: result.code,
        status: response.status,
        traceId: result.traceId,
      });
    }

    if (result.code !== 0) {
      throw new ApiError('业务处理未成功', {
        kind: 'BUSINESS',
        code: result.code,
        status: response.status,
        traceId: result.traceId,
      });
    }

    return result.data as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }

    if (isAbortError(error)) {
      throw new ApiError(didTimeout ? '请求超时' : '请求已取消', {
        kind: didTimeout ? 'TIMEOUT' : 'CANCELLED',
        isResultUnknown: didTimeout && isWriteRequest,
      });
    }

    throw new ApiError('网络连接失败', {
      kind: 'NETWORK',
      isResultUnknown: isWriteRequest,
    });
  } finally {
    window.clearTimeout(timeoutId);
    requestInit.signal?.removeEventListener('abort', handleExternalAbort);
  }
}
