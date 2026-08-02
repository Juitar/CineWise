export type ApiErrorKind =
  'BUSINESS' | 'CANCELLED' | 'HTTP' | 'INVALID_RESPONSE' | 'NETWORK' | 'TIMEOUT';

interface ApiErrorOptions {
  kind: ApiErrorKind;
  code?: number;
  status?: number;
  traceId?: string;
  isResultUnknown?: boolean;
}

/**
 * 普通 REST 请求的统一错误。
 *
 * 页面按 kind、status 和 code 决定后续动作，不能根据 message 文案判断业务分支。
 * isResultUnknown 为 true 时，写操作可能已经被后端执行，只能查询原操作结果。
 */
export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly code?: number;
  readonly status?: number;
  readonly traceId?: string;
  readonly isResultUnknown: boolean;

  constructor(message: string, options: ApiErrorOptions) {
    super(message);
    this.name = 'ApiError';
    this.kind = options.kind;
    this.code = options.code;
    this.status = options.status;
    this.traceId = options.traceId;
    this.isResultUnknown = options.isResultUnknown ?? false;
  }
}
