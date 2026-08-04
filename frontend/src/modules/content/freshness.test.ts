import { describe, expect, it } from 'vitest';

import type { ContentFreshness } from '../../shared/types/api';
import { getFreshnessNotices } from './freshness';

const liveFreshness: ContentFreshness = {
  source: 'NETSTART',
  sourceType: 'LIVE',
  dataTime: '2026-08-04T09:00:00+08:00',
  expiresAt: '2026-08-05T09:00:00+08:00',
  isExpired: false,
  degraded: false,
  fallbackType: null,
};

describe('getFreshnessNotices', () => {
  it('有效实时来源显示来源和更新时间', () => {
    const notices = getFreshnessNotices(liveFreshness);

    expect(notices).toHaveLength(1);
    expect(notices[0]).toMatchObject({ id: 'source', tone: 'info' });
    expect(notices[0].text).toContain('NETSTART');
  });

  it.each([
    ['MOCK', '演示数据'],
    ['CACHE', '缓存数据'],
    ['SNAPSHOT', '历史快照'],
  ] as const)('降级类型 %s 显示为%s', (fallbackType, label) => {
    const notices = getFreshnessNotices({
      ...liveFreshness,
      sourceType: fallbackType === 'CACHE' ? 'LIVE' : fallbackType,
      degraded: true,
      fallbackType,
    });

    expect(notices.map((notice) => notice.text)).toEqual(
      expect.arrayContaining(['当前为降级数据', label]),
    );
  });

  it.each([
    ['MOCK', '演示数据'],
    ['SNAPSHOT', '历史快照'],
  ] as const)('%s 未声明降级时仍标记非实时来源和异常组合', (sourceType, label) => {
    const notices = getFreshnessNotices({
      ...liveFreshness,
      sourceType,
      degraded: false,
      fallbackType: null,
    });

    expect(notices.map((notice) => notice.text)).toEqual(
      expect.arrayContaining([label, '来源尚未验证']),
    );
    expect(notices.some((notice) => notice.id === 'source')).toBe(false);
  });

  it('LIVE 从缓存降级时不显示为实时来源', () => {
    const notices = getFreshnessNotices({
      ...liveFreshness,
      degraded: true,
      fallbackType: 'CACHE',
    });

    expect(notices.map((notice) => notice.text)).toEqual(['当前为降级数据', '缓存数据']);
    expect(notices.some((notice) => notice.id === 'source')).toBe(false);
  });

  it('LIVE 的 degraded 与 fallbackType 矛盾时拒绝显示实时来源', () => {
    const notices = getFreshnessNotices({
      ...liveFreshness,
      degraded: false,
      fallbackType: 'CACHE',
    });

    expect(notices.map((notice) => notice.text)).toEqual(['缓存数据', '来源尚未验证']);
    expect(notices.some((notice) => notice.id === 'source')).toBe(false);
  });

  it('过期、降级和来源未验证可以同时显示', () => {
    const notices = getFreshnessNotices({
      source: '',
      sourceType: 'UNKNOWN' as 'LIVE',
      dataTime: 'invalid',
      expiresAt: 'invalid',
      isExpired: true,
      degraded: true,
      fallbackType: 'MOCK',
    });

    expect(notices.map((notice) => notice.text)).toEqual(
      expect.arrayContaining([
        '数据已过期，仅供参考',
        '当前为降级数据',
        '演示数据',
        '来源尚未验证',
      ]),
    );
  });
});
