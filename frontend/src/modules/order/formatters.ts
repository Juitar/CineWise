const BUSINESS_TIME_ZONE = 'Asia/Shanghai';
const BUSINESS_OFFSET = '+08:00';
const calendarDatePattern = /^(\d{4})-(\d{2})-(\d{2})/;
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

const businessTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: BUSINESS_TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

function hasValidCalendarDate(value: string): boolean {
  const match = calendarDatePattern.exec(value);
  if (!match) {
    return false;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const candidate = new Date(Date.UTC(year, month - 1, day));
  return (
    candidate.getUTCFullYear() === year &&
    candidate.getUTCMonth() === month - 1 &&
    candidate.getUTCDate() === day
  );
}

/**
 * 将后端交易时间解析为时间点。
 *
 * 不带偏移量的 Java LocalDateTime 按 CineWise 固定业务时区解释；非法日历日期直接拒绝，
 * 避免 JavaScript 自动把 2 月 31 日滚动到 3 月后向用户展示错误期限。
 */
export function parseOrderDateTime(value: string | null | undefined): Date | null {
  if (!value || !hasValidCalendarDate(value)) {
    return null;
  }
  const localDateTimeMatch = localDateTimePattern.exec(value);
  const normalizedValue = localDateTimeMatch
    ? `${value.replace(' ', 'T')}${BUSINESS_OFFSET}`
    : value;
  const date = new Date(normalizedValue);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date;
}

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
  const date = parseOrderDateTime(value);
  if (!date) {
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

/**
 * 以固定业务时区展示订单时间的时分部分。
 * 非法或缺失值使用调用方提供的降级文案，避免直接构造 Date 产生错误时区或 Invalid Date。
 */
export function formatOrderTime(
  value: string | null | undefined,
  fallback = '支付期限以订单信息为准',
): string {
  const date = parseOrderDateTime(value);
  if (!date) {
    return fallback;
  }
  return businessTimeFormatter.format(date);
}
