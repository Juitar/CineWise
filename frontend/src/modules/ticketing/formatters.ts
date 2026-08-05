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
