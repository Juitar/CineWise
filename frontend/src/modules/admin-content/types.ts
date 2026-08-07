export interface ContentSourceStatus {
  provider: string;
  resourceType: string;
  cityName: string;
  status: string;
  startedAt: string | null;
  finishedAt: string | null;
  lastSuccessAt: string | null;
  successCount: number;
  failureCount: number;
  failureCategory: string | null;
  dataTime: string | null;
  expiresAt: string | null;
  isExpired: boolean;
  licenseNotice: string;
}

export interface ContentSyncTask {
  syncId: string;
  clientRequestId: string;
  cityName: string;
  status: string;
  startedAt: string | null;
  finishedAt: string | null;
  successCount: number;
  failureCount: number;
  failureCategory: string | null;
}
