import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  PageEmpty,
  PageError,
  PageForbidden,
  PageLoading,
  PageNotFound,
  PageRefreshErrorNotice,
  PageRefreshingNotice,
  PageStaleNotice,
} from './PageState';

describe('PageState', () => {
  afterEach(() => cleanup());

  it('首次加载使用有可访问名称的 Skeleton 状态', () => {
    const { container } = render(<PageLoading label="影片加载中" />);

    expect(screen.getByRole('status', { name: '影片加载中' })).toBeInTheDocument();
    expect(container.querySelector('.ant-skeleton')).toBeInTheDocument();
  });

  it('空数据展示标题、说明和调整条件操作', () => {
    const onAction = vi.fn();
    render(
      <PageEmpty
        actionLabel="调整条件"
        description="当前筛选没有结果"
        onAction={onAction}
        title="暂无影片"
      />,
    );

    expect(screen.getByText('暂无影片')).toBeInTheDocument();
    expect(screen.getByText('当前筛选没有结果')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '调整条件' }));
    expect(onAction).toHaveBeenCalledOnce();
  });

  it('失败状态使用固定标题并显示问题编号', () => {
    render(<PageError description="请稍后重试" traceId="trace-page-1" />);

    expect(screen.getByText('页面加载失败')).toBeInTheDocument();
    expect(screen.getByText('请稍后重试')).toBeInTheDocument();
    expect(screen.getByText('问题编号：trace-page-1')).toBeInTheDocument();
  });

  it('失败状态允许用户手动重试', () => {
    const onRetry = vi.fn();
    render(<PageError onRetry={onRetry} />);

    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('403 明确无权限且不提供登录按钮', () => {
    render(<PageForbidden />);

    expect(screen.getByText('无权访问')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /登录/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /登录/ })).not.toBeInTheDocument();
  });

  it('404 明确资源不存在并允许返回列表', () => {
    const onAction = vi.fn();
    render(<PageNotFound actionLabel="返回列表" onAction={onAction} />);

    expect(screen.getByText('资源不存在')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '返回列表' }));
    expect(onAction).toHaveBeenCalledOnce();
  });

  it.each([
    ['stale', '数据已失效'],
    ['pending-validation', '数据待校验'],
  ] as const)('数据状态 %s 显示固定提示', (status, title) => {
    render(<PageStaleNotice status={status} />);

    expect(screen.getByText(title)).toBeInTheDocument();
  });

  it('已有数据刷新时显示独立提示', () => {
    render(<PageRefreshingNotice />);

    expect(screen.getByText('正在刷新')).toBeInTheDocument();
    expect(screen.getByText('正在获取最新数据，当前内容仍可查看。')).toBeInTheDocument();
  });

  it('刷新失败明确旧数据仍在展示并允许重试', () => {
    const onRetry = vi.fn();
    render(<PageRefreshErrorNotice onRetry={onRetry} traceId="trace-refresh-1" />);

    expect(screen.getByText('刷新失败，旧数据仍在展示')).toBeInTheDocument();
    expect(screen.getByText('问题编号：trace-refresh-1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('危险服务端文本只按文本显示，不创建或执行 HTML', () => {
    const dangerousText = '<img src=x onerror="window.__pageStateAttack=true">';
    const { container } = render(<PageError description={dangerousText} />);

    expect(screen.getByText(dangerousText)).toBeInTheDocument();
    expect(container.querySelector('img')).not.toBeInTheDocument();
    expect(
      (window as typeof window & { __pageStateAttack?: boolean }).__pageStateAttack,
    ).toBeUndefined();
  });

  it('桌面和移动共用同一响应式结构', () => {
    const { container } = render(<PageLoading />);
    const state = container.querySelector('.page-state');

    expect(state).toHaveClass('page-state--loading');
    expect(state?.querySelectorAll('.page-state-content')).toHaveLength(1);
  });
});
