/** 只允许同源图片或 HTTPS 海报，避免加载不安全或无法解析的外部地址。 */
export function safePosterUrl(posterUrl: string | null): string | null {
  if (!posterUrl) {
    return null;
  }

  try {
    const url = new URL(posterUrl, window.location.origin);
    if (url.protocol !== 'https:' && url.origin !== window.location.origin) {
      return null;
    }
    return url.href;
  } catch {
    return null;
  }
}
