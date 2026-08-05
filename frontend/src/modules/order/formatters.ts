const BUSINESS_TIME_ZONE = 'Asia/Shanghai';
const BUSINESS_OFFSET = '+08:00';
const localDateTimePattern =
  /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::\d{2}(?:\.\d{1,9})?)?$/;

const businessDateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: BUSINESS_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/**
 * 以固定业务时区展示服务端时间，避免同一场次随浏览器所在时区发生偏移。
 * 非法或缺失值使用明确降级文案，不能让订单页面因单条异常数据崩溃。
 */
export function formatOrderDateTime(
  value: string | null | undefined,
  fallback = '时间信息暂不可用',
): string {
  if (!value) {
    return fallback;
  }
  const localDateTimeMatch = localDateTimePattern.exec(value);
  // Java LocalDateTime 不携带偏移量；该字段按 CineWise 的上海业务时区解释。
  const normalizedValue = localDateTimeMatch
    ? `${value.replace(' ', 'T')}${BUSINESS_OFFSET}`
    : value;
  const date = new Date(normalizedValue);
  if (Number.isNaN(date.getTime())) {
    return fallback;
  }
  const parts = Object.fromEntries(
    businessDateTimeFormatter.formatToParts(date).map((part) => [part.type, part.value]),
  );
  const formatted = `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
  if (localDateTimeMatch) {
    const expected = `${localDateTimeMatch[1]}-${localDateTimeMatch[2]}-${localDateTimeMatch[3]} ${localDateTimeMatch[4]}:${localDateTimeMatch[5]}`;
    return formatted === expected ? formatted : fallback;
  }
  return formatted;
}
