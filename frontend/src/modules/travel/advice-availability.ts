const ADVICE_LEAD_TIME_MS = 2 * 60 * 60 * 1000;

/** 出行建议仅在开场前两小时内生成；无效时间不得提前展示入口。 */
export function isTravelAdviceAvailable(showStartTime: string, now = Date.now()): boolean {
  const startTime = Date.parse(showStartTime);
  return !Number.isNaN(startTime) && startTime - now <= ADVICE_LEAD_TIME_MS;
}
