import { formatOrderDateTime } from '../order/formatters';

/**
 * 场次时间与订单时间使用同一业务时区规则，避免页面按浏览器时区各自格式化。
 */
export function formatShowTime(value: string | null | undefined, fallback = '时间待确认'): string {
  const formatted = formatOrderDateTime(value, fallback);
  if (formatted === fallback) {
    return fallback;
  }
  return formatted.slice(-5);
}

/**
 * 展示场次所属日期。
 *
 * 场次列表不能只显示时分：跨日期的相同场次会看起来完全重复，
 * 因此日期同样必须按统一业务时区从服务端时间格式化得到。
 */
export function formatShowDate(value: string | null | undefined, fallback = '日期待确认'): string {
  const formatted = formatOrderDateTime(value, fallback);
  if (formatted === fallback) {
    return fallback;
  }
  return formatted.slice(0, 10);
}
