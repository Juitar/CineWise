import { ApiError } from './ApiError';

type QueryPrimitive = boolean | number | string;
type QueryValue = QueryPrimitive | null | readonly QueryPrimitive[] | undefined;

export interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  /** 允许明确声明的成功写接口不返回 data 字段。其他接口仍要求完整响应数据。 */
  allowEmptyResponse?: boolean;
  body?: unknown;
  handleUnauthorized?: boolean;
  query?: Record<string, QueryValue>;
  skipCsrf?: boolean;
  timeoutMs?: number;
}

interface CsrfTokenPayload {
  headerName: string;
  token: string;
}

interface ApiEnvelopeMetadata {
  code: number;
  message: string;
  traceId: string;
}

type ApiEnvelope<T> = ApiEnvelopeMetadata & ({ data: T } | { data?: never });

type UnauthorizedHandler = () => Promise<void> | void;

const DEFAULT_TIMEOUT_MS = 10_000;
const READ_METHODS = new Set(['GET', 'HEAD']);
let csrfToken: CsrfTokenPayload | null = null;
let csrfTokenRequest: Promise<CsrfTokenPayload> | null = null;
let unauthorizedHandler: UnauthorizedHandler | null = null;
let unauthorizedHandling: Promise<void> | null = null;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isApiEnvelope<T>(value: unknown): value is ApiEnvelope<T> {
  return (
    isRecord(value) &&
    typeof value.code === 'number' &&
    typeof value.message === 'string' &&
    typeof value.traceId === 'string'
  );
}

function hasRequiredResponseData<T>(
  result: ApiEnvelope<T>,
): result is ApiEnvelopeMetadata & { data: Exclude<T, null> } {
  return 'data' in result && result.data !== null;
}

function getSafeErrorMessage(message: string, fallback: string): string {
  const trimmedMessage = message.trim();
  return trimmedMessage.length > 0 && trimmedMessage.length <= 500 ? trimmedMessage : fallback;
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

async function readApiEnvelope<T>(response: Response): Promise<ApiEnvelope<T>> {
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

  if (!isApiEnvelope<T>(payload)) {
    throw new ApiError('服务返回的数据格式不正确', {
      kind: 'INVALID_RESPONSE',
      status: response.status,
      traceId,
    });
  }

  return payload;
}

function isCsrfTokenPayload(value: unknown): value is CsrfTokenPayload {
  return isRecord(value) && typeof value.headerName === 'string' && typeof value.token === 'string';
}

async function getCsrfToken(): Promise<CsrfTokenPayload> {
  if (csrfToken) {
    return csrfToken;
  }

  if (!csrfTokenRequest) {
    // CSRF 获取也复用唯一公共客户端；skipCsrf 防止递归获取 Token。
    // eslint-disable-next-line @typescript-eslint/no-use-before-define
    csrfTokenRequest = apiRequest<unknown>('/api/v1/auth/csrf', {
      handleUnauthorized: false,
      skipCsrf: true,
    })
      .then((payload) => {
        if (!isCsrfTokenPayload(payload) || !payload.headerName || !payload.token) {
          throw new ApiError('服务返回的 CSRF Token 格式不正确', {
            kind: 'INVALID_RESPONSE',
          });
        }
        csrfToken = payload;
        return payload;
      })
      .finally(() => {
        csrfTokenRequest = null;
      });
  }

  return csrfTokenRequest;
}

async function runUnauthorizedHandler(): Promise<void> {
  if (!unauthorizedHandler) {
    return;
  }

  if (!unauthorizedHandling) {
    unauthorizedHandling = Promise.resolve(unauthorizedHandler()).finally(() => {
      unauthorizedHandling = null;
    });
  }

  await unauthorizedHandling;
}

/**
 * 为不能使用普通 REST 解包的同源流请求提供当前 CSRF Header。
 *
 * Agent POST SSE 只读取 Header 名和值，不维护第二份 Token 缓存。
 */
export async function getCsrfRequestHeaders(): Promise<Headers> {
  const currentCsrfToken = await getCsrfToken();
  return new Headers({ [currentCsrfToken.headerName]: currentCsrfToken.token });
}

/** 让流式客户端复用公共客户端的并发 401 单次清理。 */
export async function handleUnauthorizedResponse(): Promise<void> {
  await runUnauthorizedHandler();
}

/** 清除仅存在于运行内存中的 CSRF Token。登录、登出或会话切换后调用。 */
export function clearCsrfToken(): void {
  csrfToken = null;
}

/**
 * 注册全局 401 处理器，同一批并发 401 只执行一次会话清理。
 * 返回的函数只会移除本次注册，避免组件卸载时清除后续 Provider 的处理器。
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): () => void {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) {
      unauthorizedHandler = null;
    }
  };
}

/**
 * 发送普通 REST 请求并解包后端统一响应。
 *
 * 该函数始终携带 HttpOnly Cookie，不读取或保存 Token，也不会自动重试写操作。
 * 写请求超时或断网时会标记 isResultUnknown，调用模块必须使用原业务 ID 查询结果。
 */
export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const {
    allowEmptyResponse = false,
    body,
    handleUnauthorized = true,
    query,
    skipCsrf = false,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    ...requestInit
  } = options;
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
  if (isWriteRequest && !skipCsrf) {
    const currentCsrfToken = await getCsrfToken();
    headers.set(currentCsrfToken.headerName, currentCsrfToken.token);
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
    const result = await readApiEnvelope<T>(response);

    const isPermissionDenied = response.status === 403 && result.code === 201007;
    // 服务端通常会在写响应后更新 CSRF Cookie；201007 仅表示权限不足，保留当前 Token。
    if (isWriteRequest && !isPermissionDenied) {
      clearCsrfToken();
    }

    if (response.status === 401 && handleUnauthorized) {
      try {
        await runUnauthorizedHandler();
      } catch {
        // 会话清理失败不能替换后端返回的 401 结构化错误。
      }
    }

    if (isWriteRequest && response.status === 403 && result.code === 201009) {
      clearCsrfToken();
      try {
        await getCsrfToken();
      } catch {
        // 保留原始 201009，让页面提示用户重新确认；刷新失败不替换原错误。
      }
    }

    if (!response.ok) {
      throw new ApiError(getSafeErrorMessage(result.message, '请求未成功，请稍后重试'), {
        kind: 'HTTP',
        code: result.code,
        status: response.status,
        traceId: result.traceId,
      });
    }

    if (result.code !== 0) {
      throw new ApiError(getSafeErrorMessage(result.message, '业务处理未成功'), {
        kind: 'BUSINESS',
        code: result.code,
        status: response.status,
        traceId: result.traceId,
      });
    }

    if (!hasRequiredResponseData(result)) {
      if (allowEmptyResponse && response.ok && result.code === 0) {
        return undefined as T;
      }
      throw new ApiError('服务返回的数据格式不正确', {
        kind: 'INVALID_RESPONSE',
        status: response.status,
        traceId: result.traceId,
      });
    }

    return result.data;
  } catch (error) {
    if (isWriteRequest && (!(error instanceof ApiError) || error.kind === 'INVALID_RESPONSE')) {
      clearCsrfToken();
    }
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
