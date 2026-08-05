import type {
  ContentFreshness,
  ContentFallbackType,
  ContentSourceType,
} from '../../shared/types/api';

const SOURCE_TYPES = new Set<ContentSourceType>(['LIVE', 'MOCK', 'SNAPSHOT']);
const FALLBACK_TYPES = new Set<ContentFallbackType>(['CACHE', 'MOCK', 'SNAPSHOT']);

export type FreshnessNoticeTone = 'info' | 'warning';

/** 页面可直接渲染的来源提示；id 保证组合提示使用稳定 key。 */
export interface FreshnessNotice {
  id: 'degraded' | 'expired' | 'fallback' | 'source' | 'unverified';
  text: string;
  tone: FreshnessNoticeTone;
}

function isValidDateTime(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && !Number.isNaN(Date.parse(value));
}

function isKnownSourceType(value: unknown): value is ContentSourceType {
  return typeof value === 'string' && SOURCE_TYPES.has(value as ContentSourceType);
}

function isKnownFallbackType(value: unknown): value is ContentFallbackType {
  return typeof value === 'string' && FALLBACK_TYPES.has(value as ContentFallbackType);
}

function formatDataTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function fallbackLabel(fallbackType: ContentFallbackType): string {
  switch (fallbackType) {
    case 'MOCK':
      return '演示数据';
    case 'CACHE':
      return '缓存数据';
    case 'SNAPSHOT':
      return '历史快照';
    default: {
      const exhaustiveCheck: never = fallbackType;
      return exhaustiveCheck;
    }
  }
}

/**
 * 校验来源类型与降级字段是否表达同一个事实。
 *
 * LIVE 可以直接返回，也可以从缓存或快照降级读取；MOCK 和 SNAPSHOT 本身已经是非实时来源，
 * 必须带对应 fallbackType。任何矛盾组合都不能按实时来源展示。
 */
function isConsistentSourceCombination(
  sourceType: ContentSourceType,
  degraded: boolean,
  fallbackType: ContentFallbackType | null,
): boolean {
  if (degraded !== (fallbackType !== null)) {
    return false;
  }

  switch (sourceType) {
    case 'LIVE':
      return fallbackType === null || fallbackType === 'CACHE' || fallbackType === 'SNAPSHOT';
    case 'MOCK':
      return fallbackType === 'MOCK';
    case 'SNAPSHOT':
      return fallbackType === 'SNAPSHOT';
    default: {
      const exhaustiveCheck: never = sourceType;
      return exhaustiveCheck;
    }
  }
}

/** 优先按来源类型标记非实时数据；LIVE 降级时再使用 fallbackType。 */
function displayedFallbackType(
  sourceType: unknown,
  fallbackType: unknown,
): ContentFallbackType | null {
  if (sourceType === 'MOCK') {
    return 'MOCK';
  }
  if (sourceType === 'SNAPSHOT') {
    return 'SNAPSHOT';
  }
  return isKnownFallbackType(fallbackType) ? fallbackType : null;
}

/**
 * 将后端内容来源转换为页面提示。
 *
 * 过期、降级和来源未验证互不排斥，避免只显示其中一个风险而让用户误以为数据是实时的。
 */
export function getFreshnessNotices(
  freshness: Partial<ContentFreshness> | null | undefined,
): FreshnessNotice[] {
  if (!freshness) {
    return [{ id: 'unverified', text: '来源尚未验证', tone: 'warning' }];
  }

  const notices: FreshnessNotice[] = [];
  // 即使 TypeScript DTO 声明了字段，HTTP 响应仍属于外部输入；运行时校验失败必须显式提示，
  // 不能因类型断言成功就把未知来源展示成实时数据。
  const hasValidFields =
    typeof freshness.source === 'string' &&
    freshness.source.trim().length > 0 &&
    isKnownSourceType(freshness.sourceType) &&
    isValidDateTime(freshness.dataTime) &&
    isValidDateTime(freshness.expiresAt) &&
    typeof freshness.isExpired === 'boolean' &&
    typeof freshness.degraded === 'boolean' &&
    (freshness.fallbackType === null || isKnownFallbackType(freshness.fallbackType));
  const sourceVerified =
    hasValidFields &&
    isConsistentSourceCombination(
      freshness.sourceType as ContentSourceType,
      freshness.degraded as boolean,
      freshness.fallbackType as ContentFallbackType | null,
    );

  if (
    sourceVerified &&
    freshness.sourceType === 'LIVE' &&
    typeof freshness.source === 'string' &&
    isValidDateTime(freshness.dataTime)
  ) {
    const sourceText =
      freshness.degraded === true
        ? `原始来源：${freshness.source}，更新时间：${formatDataTime(freshness.dataTime)}`
        : `来源：${freshness.source}，更新于 ${formatDataTime(freshness.dataTime)}`;
    notices.push({
      id: 'source',
      text: sourceText,
      tone: 'info',
    });
  }

  if (freshness.isExpired === true) {
    notices.push({ id: 'expired', text: '数据已过期，仅供参考', tone: 'warning' });
  }

  if (freshness.degraded === true) {
    notices.push({ id: 'degraded', text: '当前为降级数据', tone: 'warning' });
  }

  // MOCK/SNAPSHOT 即使与 degraded 字段矛盾也必须明确标记，避免非实时卡片在页面上没有来源说明。
  const fallbackType = displayedFallbackType(freshness.sourceType, freshness.fallbackType);
  if (fallbackType) {
    notices.push({
      id: 'fallback',
      text: fallbackLabel(fallbackType),
      tone: 'warning',
    });
  }

  if (!sourceVerified) {
    notices.push({ id: 'unverified', text: '来源尚未验证', tone: 'warning' });
  }

  return notices;
}
