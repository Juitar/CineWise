let fallbackUuidSequence = 0;

/**
 * 生成订单写操作使用的 UUID。
 *
 * `randomUUID` 在 HTTP IP 演示环境中可能不存在，因此按能力降级到
 * `getRandomValues`，最后使用页面内序列兜底，确保幂等标识仍保持 UUID 形状。
 */
export function createOrderUuid(): string {
  const cryptoApi = globalThis.crypto;
  if (typeof cryptoApi?.randomUUID === 'function') {
    return cryptoApi.randomUUID();
  }

  if (typeof cryptoApi?.getRandomValues === 'function') {
    const bytes = new Uint8Array(16);
    cryptoApi.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
    return [
      hex.slice(0, 8),
      hex.slice(8, 12),
      hex.slice(12, 16),
      hex.slice(16, 20),
      hex.slice(20),
    ].join('-');
  }

  fallbackUuidSequence = (fallbackUuidSequence + 1) % 0x1000000;
  const timestamp = Date.now().toString(16).padStart(6, '0').slice(-6);
  const sequence = fallbackUuidSequence.toString(16).padStart(6, '0');
  return `00000000-0000-4000-8000-${timestamp}${sequence}`;
}
