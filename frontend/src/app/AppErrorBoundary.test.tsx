import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { AppErrorBoundary } from './AppErrorBoundary';

class FailedAppErrorBoundary extends AppErrorBoundary {
  state = {
    hasError: true,
  };
}

describe('AppErrorBoundary', () => {
  it('子组件渲染失败时显示可恢复的安全页面', () => {
    expect(AppErrorBoundary.getDerivedStateFromError()).toEqual({ hasError: true });

    render(
      <FailedAppErrorBoundary>
        <div>不会显示的页面内容</div>
      </FailedAppErrorBoundary>,
    );

    expect(screen.getByRole('heading', { level: 1, name: '页面暂时无法显示' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '刷新页面' })).toBeEnabled();
  });
});
