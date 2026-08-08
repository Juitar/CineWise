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
});
