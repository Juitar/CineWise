const amountPattern = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/;

/**
 * 按分计算订单金额，避免浮点误差；非法价格或数量统一返回安全的 0.00。
 */
export function calculateOrderTotalAmount(
  basePrice: string | undefined,
  seatCount: number,
): string {
  if (!basePrice || !Number.isSafeInteger(seatCount) || seatCount <= 0) {
    return '0.00';
  }
  if (!amountPattern.test(basePrice)) {
    return '0.00';
  }

  const [yuan, fraction = ''] = basePrice.split('.');
  const cents = BigInt(yuan) * 100n + BigInt(fraction.padEnd(2, '0'));
  const totalCents = cents * BigInt(seatCount);
  return `${totalCents / 100n}.${(totalCents % 100n).toString().padStart(2, '0')}`;
}
