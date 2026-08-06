import { describe, expect, it } from 'vitest';

import { safePosterUrl } from './poster';

describe('safePosterUrl', () => {
  it('允许 HTTPS 和同源海报', () => {
    expect(safePosterUrl('https://images.example.com/poster.webp')).toBe(
      'https://images.example.com/poster.webp',
    );
    expect(safePosterUrl('/assets/poster.webp')).toBe(
      `${window.location.origin}/assets/poster.webp`,
    );
  });

  it('拒绝 HTTP 外链、非法 URL 和空值', () => {
    expect(safePosterUrl('http://images.example.com/poster.webp')).toBeNull();
    expect(safePosterUrl('http://%')).toBeNull();
    expect(safePosterUrl(null)).toBeNull();
  });
});
