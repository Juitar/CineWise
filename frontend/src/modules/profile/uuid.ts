let fallbackUuidSequence = 0;

/**
 * 生成画像写操作使用的幂等 UUID。
 *
 * 远端演示环境可能通过 HTTP IP 访问，此时浏览器不保证提供 randomUUID；
 * 优先使用 Web Crypto，能力不足时仍生成页面内唯一的 UUID 形状标识。
 */
export function createProfileUuid(): string {
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
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  fallbackUuidSequence = (fallbackUuidSequence + 1) % 0x1000000;
  const timestamp = Date.now().toString(16).padStart(6, '0').slice(-6);
  const sequence = fallbackUuidSequence.toString(16).padStart(6, '0');
  return `00000000-0000-4000-8000-${timestamp}${sequence}`;
}
