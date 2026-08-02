import { Component, type PropsWithChildren } from 'react';

import './AppErrorBoundary.css';

interface AppErrorBoundaryState {
  hasError: boolean;
}

/**
 * 捕获应用壳层未处理的渲染错误，避免全站白屏。
 *
 * 兜底视图不依赖组件库，确保 Provider 或组件库本身失败时仍能刷新页面。
 */
export class AppErrorBoundary extends Component<PropsWithChildren, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = {
    hasError: false,
  };

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { hasError: true };
  }

  private handleReload = () => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <main className="app-error-boundary" role="alert">
          <h1 className="app-error-boundary-title">页面暂时无法显示</h1>
          <p className="app-error-boundary-description">请刷新页面后重试。</p>
          <button className="app-error-boundary-button" onClick={this.handleReload} type="button">
            刷新页面
          </button>
        </main>
      );
    }

    return this.props.children;
  }
}
