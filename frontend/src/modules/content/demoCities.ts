/**
 * 答辩演示允许切换的受控城市。
 *
 * 城市代码是内容查询的公开筛选条件；这里故意不接收地点原文或 Provider 城市标识，
 * 避免浏览器把位置隐私或第三方内部标识带入 URL 与页面状态。
 */
export const DEMO_CITY_OPTIONS = [
  { code: '430100', name: '长沙' },
  { code: '330100', name: '杭州' },
] as const;

export type DemoCityCode = (typeof DEMO_CITY_OPTIONS)[number]['code'];
export type DemoCity = (typeof DEMO_CITY_OPTIONS)[number];

export const DEFAULT_DEMO_CITY = DEMO_CITY_OPTIONS[0];

/** 返回固定城市，未知或缺失的 URL 参数安全降级为长沙。 */
export function resolveDemoCity(value: string | null | undefined): DemoCity {
  return DEMO_CITY_OPTIONS.find((city) => city.code === value) ?? DEFAULT_DEMO_CITY;
}

/**
 * 构造城市切换后的安全页面地址。
 *
 * 仅首页和影院页可以原地切换内容筛选；订单、电子票和出行页绑定已有业务事实，
 * 切换浏览城市时统一跳到影院页，避免错误地改写这些页面的业务上下文。
 */
export function buildDemoCityDestination(
  pathname: string,
  search: string,
  cityCode: DemoCityCode,
): string {
  if (pathname !== '/' && pathname !== '/cinemas') {
    return `/cinemas?location=${cityCode}`;
  }

  const searchParams = new URLSearchParams(search);
  searchParams.set('location', cityCode);
  searchParams.delete('page');
  const query = searchParams.toString();
  return query ? `${pathname}?${query}` : pathname;
}
