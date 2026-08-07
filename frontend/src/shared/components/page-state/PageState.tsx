import { Alert, Button, Empty, Skeleton } from 'antd';
import React from 'react';

import './PageState.css';

interface PageActionProps {
  actionLabel?: string;
  onAction?: () => void;
}

interface PageMessageProps {
  description?: string;
}

export interface PageLoadingProps extends PageMessageProps {
  label?: string;
}

export interface PageEmptyProps extends PageActionProps, PageMessageProps {
  title?: string;
}

export interface PageErrorProps extends PageMessageProps {
  onRetry?: () => void;
  retryLabel?: string;
  traceId?: string;
}

export type PageForbiddenProps = PageActionProps & PageMessageProps;

export type PageNotFoundProps = PageActionProps & PageMessageProps;

export interface PageRefreshErrorNoticeProps extends PageMessageProps {
  onRetry?: () => void;
  traceId?: string;
}

export interface PageStaleNoticeProps extends PageMessageProps {
  status: 'stale' | 'pending-validation';
}

function PageAction({ actionLabel, onAction }: PageActionProps) {
  if (!actionLabel || !onAction) {
    return null;
  }

  return (
    <Button aria-label={actionLabel} className="page-state-action" onClick={onAction}>
      {actionLabel}
    </Button>
  );
}

function ProblemNumber({ traceId }: { traceId?: string }) {
  const problemNumber = traceId?.trim();
  if (!problemNumber) {
    return null;
  }

  return <span className="page-state-trace-id">问题编号：{problemNumber.slice(0, 100)}</span>;
}

/**
 * 展示页面首次查询的加载占位。
 *
 * 仅在页面还没有可展示数据时使用；已有数据刷新必须保留内容并改用
 * `PageRefreshingNotice`。
 */
export function PageLoading({
  description = '正在获取最新内容，请稍候。',
  label = '页面加载中',
}: PageLoadingProps) {
  return (
    <section
      aria-label={label}
      aria-live="polite"
      className="page-state page-state--loading"
      role="status"
    >
      <div className="page-state-content">
        <span className="page-state-title">{label}</span>
        <span className="page-state-description">{description}</span>
        <Skeleton active paragraph={{ rows: 3 }} title />
      </div>
    </section>
  );
}

/**
 * 展示查询成功后的空数据状态。
 *
 * 请求失败不能使用本组件；调整条件或返回操作由调用页面显式提供。
 */
export function PageEmpty({
  actionLabel,
  description = '当前条件下没有可展示的数据。',
  onAction,
  title = '暂无数据',
}: PageEmptyProps) {
  return (
    <section className="page-state page-state--empty">
      <div className="page-state-content">
        <Empty description={false} image={Empty.PRESENTED_IMAGE_SIMPLE}>
          <span className="page-state-title">{title}</span>
          <span className="page-state-description">{description}</span>
          <PageAction actionLabel={actionLabel} onAction={onAction} />
        </Empty>
      </div>
    </section>
  );
}

/**
 * 展示没有旧数据可保留时的查询失败状态。
 *
 * 标题固定为安全文案；调用方不得把服务端错误正文或堆栈传入 description，
 * 只可传面向用户的短说明、traceId 和手动重试回调。
 */
export function PageError({
  description = '暂时无法加载内容，请稍后重试。',
  onRetry,
  retryLabel = '重试',
  traceId,
}: PageErrorProps) {
  return (
    <section className="page-state page-state--error" role="alert">
      <div className="page-state-content">
        <span className="page-state-title">页面加载失败</span>
        <span className="page-state-description">{description}</span>
        <ProblemNumber traceId={traceId} />
        <PageAction actionLabel={onRetry ? retryLabel : undefined} onAction={onRetry} />
      </div>
    </section>
  );
}

/** 展示 403 状态；保持当前登录态，不提供登录按钮或登录跳转。 */
export function PageForbidden({
  actionLabel,
  description = '当前账号没有访问此内容的权限。',
  onAction,
}: PageForbiddenProps) {
  return (
    <section className="page-state page-state--forbidden" role="alert">
      <div className="page-state-content">
        <span className="page-state-title">无权访问</span>
        <span className="page-state-description">{description}</span>
        <PageAction actionLabel={actionLabel} onAction={onAction} />
      </div>
    </section>
  );
}

/** 展示 404 状态；返回列表等安全操作由调用页面提供。 */
export function PageNotFound({
  actionLabel,
  description = '要查看的内容不存在或已被移除。',
  onAction,
}: PageNotFoundProps) {
  return (
    <section className="page-state page-state--not-found" role="alert">
      <div className="page-state-content">
        <span className="page-state-title">资源不存在</span>
        <span className="page-state-description">{description}</span>
        <PageAction actionLabel={actionLabel} onAction={onAction} />
      </div>
    </section>
  );
}

/** 已有数据刷新时显示轻量提示；调用方必须继续渲染旧数据。 */
export function PageRefreshingNotice({
  description = '正在获取最新数据，当前内容仍可查看。',
}: PageMessageProps) {
  return (
    <Alert
      className="page-state-notice"
      description={description}
      message="正在刷新"
      showIcon
      type="info"
    />
  );
}

/** 刷新失败时说明旧数据仍在展示，并可提供问题编号和手动重试。 */
export function PageRefreshErrorNotice({
  description = '刷新失败，旧数据仍在展示。',
  onRetry,
  traceId,
}: PageRefreshErrorNoticeProps) {
  return (
    <Alert
      action={
        onRetry ? (
          <Button aria-label="重试" className="page-state-action" onClick={onRetry}>
            重试
          </Button>
        ) : undefined
      }
      className="page-state-notice"
      description={
        <span className="page-state-notice-description">
          <span>{description}</span>
          <ProblemNumber traceId={traceId} />
        </span>
      }
      message="刷新失败，旧数据仍在展示"
      showIcon
      type="error"
    />
  );
}

/**
 * 展示通用失效或待校验提示。
 *
 * 本组件只说明数据状态，不读取业务状态，也不决定任何按钮是否可用。
 */
export function PageStaleNotice({ description, status }: PageStaleNoticeProps) {
  const isStale = status === 'stale';
  return (
    <Alert
      className="page-state-notice"
      description={
        description ?? (isStale ? '当前数据已失效，请重新获取。' : '当前数据需要联网校验后再确认。')
      }
      message={isStale ? '数据已失效' : '数据待校验'}
      showIcon
      type="warning"
    />
  );
}
