import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

/**
 * 设置是否为移动端视图（<=1023px）
 */
export function setMobileView(isMobile: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query) => ({
      matches: isMobile ? query.includes('max-width: 1023px') : false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

/**
 * 设置前端展示组件测试的基础环境：
 * 1. polyfill window.matchMedia，支持 AntD 响应式与警告静默
 * 2. 自动在每次测试用例结束后执行 DOM 清理，避免多用例间选择器冲突
 */
export function setupTestEnvironment() {
  setMobileView(false);

  afterEach(() => {
    cleanup();
    setMobileView(false); // Reset to default desktop
  });
}
