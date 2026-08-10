/**
 * 出行页面的来源编码来自多个 Provider，不能把内部编码直接展示给用户。
 *
 * 内容、路线、天气虽然都使用 source 字段，但同一个编码只在自己的业务类别中有含义。
 * 因此不采用单一全局表，避免 Provider 新增编码后被错误解释成另一类数据。
 *
 * 这个函数只负责显示文案；原始来源、数据时间、有效期和降级事实仍由页面接口字段表达。
 */
export type TravelSourceKind = 'CONTENT' | 'ROUTE' | 'WEATHER';

const SOURCE_LABELS: Record<TravelSourceKind, Record<string, string>> = {
  CONTENT: {
    LIVE_CONTENT: '实时内容',
    NETSTART: '猫眼',
    NETSTART_MAOYAN: '猫眼',
    DEMO_CONTENT: '演示内容',
    DEMO_CONTENT_V1: '演示内容',
  },
  ROUTE: {
    AMAP_ROUTE: '高德路线',
  },
  WEATHER: {
    AMAP_WEATHER: '高德天气',
    DEMO_WEATHER_V1: '演示天气',
    UNAVAILABLE: '暂无天气来源',
  },
};

/**
 * 未知编码也不能原样显示，统一给用户一个可理解的兜底文案。
 *
 * 返回“来源待确认”不会把演示数据伪装为实时数据；页面已有的 degraded 和 fallbackType
 * 提示仍会继续渲染。新 Provider 尚未加入映射时，用户也不会看到中英文混合的内部标识。
 */
export function formatTravelSource(
  source: string | null | undefined,
  kind: TravelSourceKind,
): string {
  const normalized = source?.trim().toUpperCase();
  return (normalized && SOURCE_LABELS[kind][normalized]) || '来源待确认';
}
