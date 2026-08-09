import { describe, expect, it } from 'vitest';

import { buildDemoCityDestination, resolveDemoCity } from './demoCities';

describe('demoCities', () => {
  it('只解析答辩允许的两个城市，未知值安全回退到长沙', () => {
    expect(resolveDemoCity('330100')).toEqual({ code: '330100', name: '杭州' });
    expect(resolveDemoCity('310000')).toEqual({ code: '430100', name: '长沙' });
  });

  it('首页和影院页切换城市时保留安全筛选并清除页码', () => {
    expect(buildDemoCityDestination('/', '', '330100')).toBe('/?location=330100');
    expect(buildDemoCityDestination('/cinemas', '?keyword=万达&page=3', '330100')).toBe(
      '/cinemas?keyword=%E4%B8%87%E8%BE%BE&location=330100',
    );
  });

  it('交易和出行页面切换城市时回到对应城市的影院浏览页', () => {
    expect(buildDemoCityDestination('/orders/10001', '?orderNo=10001', '330100')).toBe(
      '/cinemas?location=330100',
    );
  });
});
