import { Alert, Button, Empty, Skeleton, Tag } from 'antd';
import React from 'react';
import { Link, useParams } from 'umi';

import { useAvailableCinemas } from '../../modules/ticketing/useAvailableCinemas';
import type { AvailableCinema } from '../../modules/ticketing/types';
import './available-cinemas.css';

function formatDateTime(value: string | null): string {
  if (!value || Number.isNaN(Date.parse(value))) return '时间待更新';
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  );
}

function isNonRealtimeSource(source: string): boolean {
  const normalized = source.trim().toUpperCase();
  return (
    normalized.includes('MOCK') || normalized.includes('DEMO') || normalized.includes('SNAPSHOT')
  );
}

function errorMessage(status: number | undefined, code: number | undefined): string {
  if (code === 303004) return '影片资料暂时不可用';
  if (code === 306003) return '可售场次暂时不可查询';
  return '可售影院加载失败';
}

function CinemaCard({ cinema, movieId }: { cinema: AvailableCinema; movieId: string }) {
  const sourceIsNonRealtime =
    isNonRealtimeSource(cinema.contentSource) || isNonRealtimeSource(cinema.scheduleSource);
  return (
    <article className="available-cinemas-card" data-testid={`available-cinema-${cinema.cinemaId}`}>
      <div className="available-cinemas-card-main">
        <div className="available-cinemas-card-title-row">
          <h2>{cinema.name}</h2>
          {cinema.contentExpired ? <Tag color="orange">资料已过期</Tag> : null}
          {sourceIsNonRealtime ? <Tag color="purple">非实时来源</Tag> : null}
        </div>
        <address>{cinema.address || '地址待更新'}</address>
        <p>可选场次：{cinema.availableShowCount} 场</p>
        <p>最近开场：{formatDateTime(cinema.nearestStartTime)}</p>
        <p className="available-cinemas-data-time">
          影片资料：{cinema.contentSource || '来源待确认'} ·{' '}
          {formatDateTime(cinema.contentDataTime)}
        </p>
        <p className="available-cinemas-data-time">
          排期来源：{cinema.scheduleSource || '来源待确认'} ·{' '}
          {formatDateTime(cinema.scheduleDataTime)}
        </p>
      </div>
      <Link
        aria-label={`选择${cinema.name}的场次`}
        className="available-cinemas-purchase-link"
        to={`/shows?movieId=${encodeURIComponent(movieId)}&cinemaId=${encodeURIComponent(cinema.cinemaId)}`}
      >
        选择影院
      </Link>
    </article>
  );
}

export default function AvailableCinemasPage() {
  const { movieId } = useParams<{ movieId: string }>();
  const state = useAvailableCinemas(movieId);

  if (!movieId) {
    return <Alert showIcon message="影片地址无效" type="error" />;
  }

  const unavailable = state.error?.code === 303004 || state.error?.code === 306003;

  return (
    <main className="available-cinemas-page">
      <nav aria-label="面包屑" className="available-cinemas-breadcrumb">
        <Link to="/movies">影片列表</Link>
        <span aria-hidden="true"> / </span>
        <span>选择影院</span>
      </nav>
      <header className="available-cinemas-header">
        <h1>选择影院</h1>
        <p>可售场次和座位以进入下一页后的实时查询结果为准。</p>
      </header>

      {state.isOfflineSnapshot ? (
        <Alert
          className="available-cinemas-status"
          message="当前已离线，正在显示本页内存中的只读快照"
          showIcon
          type="warning"
        />
      ) : null}
      {state.isLoading ? (
        <div aria-label="可售影院加载中" className="available-cinemas-list">
          <Skeleton active paragraph={{ rows: 4 }} />
          <Skeleton active paragraph={{ rows: 4 }} />
        </div>
      ) : null}
      {state.error ? (
        <Alert
          action={<Button onClick={state.retry}>重试</Button>}
          className="available-cinemas-status"
          description={state.error.traceId ? `问题编号：${state.error.traceId}` : undefined}
          message={errorMessage(state.error.status, state.error.code)}
          showIcon
          type={unavailable ? 'warning' : 'error'}
        />
      ) : null}
      {!state.isLoading && !state.error && state.data?.records.length === 0 ? (
        <Empty description="当前没有可售影院" />
      ) : null}
      {state.data && state.data.records.length > 0 ? (
        <div className="available-cinemas-list" aria-live="polite">
          {state.data.records.map((cinema) => (
            <CinemaCard cinema={cinema} key={cinema.cinemaId} movieId={movieId} />
          ))}
        </div>
      ) : null}
    </main>
  );
}
