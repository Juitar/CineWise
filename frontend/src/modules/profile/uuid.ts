let fallbackUuidSequence = 0;

function fallbackRandomHex(length: number): string {
  let value = '';
  while (value.length < length) {
    value += Math.floor(Math.random() * 0x100000000)
      .toString(16)
      .padStart(8, '0');
  }
  return value.slice(0, length);
}

/**
 * 生成画像写操作使用的幂等 UUID。
 *
 * 远端演示环境可能通过 HTTP IP 访问，此时浏览器不保证提供 randomUUID；
 * 优先使用 Web Crypto，能力不足时仍生成页面和浏览器实例间碰撞概率足够低的 UUID 形状标识。
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
  // 此分支只生成非敏感幂等键；随机片段避免不同标签页在相同毫秒从相同序号开始时碰撞。
  const randomHex = fallbackRandomHex(18);
  return [
    randomHex.slice(0, 8),
    randomHex.slice(8, 12),
    `4${randomHex.slice(12, 15)}`,
    `8${randomHex.slice(15, 18)}`,
    `${timestamp}${sequence}`,
  ].join('-');
}
