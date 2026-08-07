import { apiRequest } from '../../shared/api/client';

export interface ExternalShowtimeImportRequest {
  showDate: string;
  cinemaIds: string[];
}

export interface ExternalShowtimeImportResponse {
  showIds: string[];
  truncated: boolean;
}

/** 管理员主动导入；公共客户端会携带 CSRF，写请求未知结果不自动重发。 */
export function importExternalShowtimeReferences(
  request: ExternalShowtimeImportRequest,
): Promise<ExternalShowtimeImportResponse> {
  return apiRequest<ExternalShowtimeImportResponse>(
    '/api/v1/admin/ticketing/external-showtimes/import',
    // Provider 可能进行一次重试并受共享限流保护；导入是管理员主动操作，允许更长等待，
    // 避免前端超时但后端已经完成写入，造成管理员重复点击。
    { body: request, method: 'POST', timeoutMs: 60_000 },
  );
}
