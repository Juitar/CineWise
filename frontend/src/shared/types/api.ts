/** 后端统一 REST 响应。成功时 code 为 0，业务失败由公共请求层转成 ApiError。 */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

/** 后端统一分页结果，page 从 1 开始。 */
export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}
