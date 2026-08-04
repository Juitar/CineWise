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
  const sourceVerified =
    typeof freshness.source === 'string' &&
    freshness.source.trim().length > 0 &&
    isKnownSourceType(freshness.sourceType) &&
    isValidDateTime(freshness.dataTime) &&
    isValidDateTime(freshness.expiresAt) &&
    typeof freshness.isExpired === 'boolean' &&
    typeof freshness.degraded === 'boolean' &&
    (freshness.fallbackType === null || isKnownFallbackType(freshness.fallbackType));

  if (
    sourceVerified &&
    freshness.sourceType === 'LIVE' &&
    typeof freshness.source === 'string' &&
    isValidDateTime(freshness.dataTime)
  ) {
    notices.push({
      id: 'source',
      text: `来源：${freshness.source}，更新于 ${formatDataTime(freshness.dataTime)}`,
      tone: 'info',
    });
  }

  if (freshness.isExpired === true) {
    notices.push({ id: 'expired', text: '数据已过期，仅供参考', tone: 'warning' });
  }

  if (freshness.degraded === true) {
    notices.push({ id: 'degraded', text: '当前为降级数据', tone: 'warning' });
    // fallbackType 说明本次实际使用的回退层，和 degraded 总提示分开渲染，便于用户区分演示、缓存和快照。
    if (isKnownFallbackType(freshness.fallbackType)) {
      notices.push({
        id: 'fallback',
        text: fallbackLabel(freshness.fallbackType),
        tone: 'warning',
      });
    }
  }

  if (!sourceVerified) {
    notices.push({ id: 'unverified', text: '来源尚未验证', tone: 'warning' });
  }

  return notices;
}
