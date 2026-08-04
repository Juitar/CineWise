import { useEffect, useState } from 'react';

/**
 * 监听浏览器媒体查询，用于在同一路由下选择桌面端或移动端视图。
 *
 * 服务端或测试环境没有 window 时先返回 false，挂载后再使用浏览器实际结果。
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return;
    }

    const mediaQueryList = window.matchMedia(query);
    const handleChange = (event: MediaQueryListEvent) => setMatches(event.matches);

    setMatches(mediaQueryList.matches);
    mediaQueryList.addEventListener('change', handleChange);

    return () => mediaQueryList.removeEventListener('change', handleChange);
  }, [query]);

  return matches;
}
