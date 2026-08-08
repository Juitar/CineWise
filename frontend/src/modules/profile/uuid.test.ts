import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createProfileUuid } from './uuid';

describe('createProfileUuid', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('优先使用 randomUUID', () => {
    vi.stubGlobal('crypto', { randomUUID: vi.fn(() => 'profile-uuid') });

    expect(createProfileUuid()).toBe('profile-uuid');
  });

  it('HTTP IP 环境缺少 randomUUID 时使用 getRandomValues 生成 UUID', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: (bytes: Uint8Array) => {
        bytes.fill(0);
        return bytes;
      },
    });

    expect(createProfileUuid()).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-8[0-9a-f]{3}-[0-9a-f]{12}$/,
    );
  });

  it('crypto 完全不可用时仍生成格式正确且连续不重复的非敏感幂等键', () => {
    vi.stubGlobal('crypto', undefined);
    vi.spyOn(Date, 'now').mockReturnValue(1_770_000_000_000);
    const random = vi.spyOn(Math, 'random');

    const identifiers = Array.from({ length: 100 }, () => createProfileUuid());

    expect(random).toHaveBeenCalled();
    expect(new Set(identifiers)).toHaveLength(100);
    identifiers.forEach((identifier) => {
      expect(identifier).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-8[0-9a-f]{3}-[0-9a-f]{12}$/,
      );
    });
  });
});
