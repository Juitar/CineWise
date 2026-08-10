import { Alert, Button, Empty, Skeleton, Tag } from 'antd';
import React from 'react';
import { Link, useLocation, useParams } from 'umi';
import { workspacePath } from '../../modules/agent/workspaceRoute';

import {
  formatContentSource,
  formatScheduleSource,
  getFreshnessNotices,
} from '../../modules/content/freshness';
import { safePosterUrl } from '../../modules/content/poster';
import { useMovieDetail } from '../../modules/content/useMovieDetail';
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

function isBusinessId(value: string | undefined): value is string {
  return value !== undefined && /^[1-9][0-9]*$/.test(value);
}

function MovieDetailCard() {
  const { movieId } = useParams<{ movieId: string }>();
  const state = useMovieDetail(isBusinessId(movieId) ? movieId : undefined);
  const movie = state.data;

  if (state.isLoading) {
    return (
      <div aria-label="影片详情加载中">
        <Skeleton active avatar paragraph={{ rows: 3 }} />
      </div>
    );
  }
  if (state.error) {
    const isNotFound = state.error.status === 404;
    return (
      <Alert
        action={<Button onClick={state.retry}>重试</Button>}
        className="available-cinemas-status"
        description={state.error.traceId ? `问题编号：${state.error.traceId}` : undefined}
        message={isNotFound ? '影片不存在' : '影片详情加载失败'}
        showIcon
        type={isNotFound ? 'warning' : 'error'}
      />
    );
  }
  if (!movie) return null;

  const notices = getFreshnessNotices(movie);
  const posterUrl = safePosterUrl(movie.posterUrl);
  return (
    <section className="movie-detail-card" aria-label="影片资料">
      {posterUrl ? <img alt={`${movie.title}海报`} src={posterUrl} /> : null}
      <div>
        <h1>{movie.title}</h1>
        <p>{movie.genres.length > 0 ? movie.genres.join(' / ') : '类型待更新'}</p>
        <p>{movie.durationMinutes === null ? '时长待更新' : `${movie.durationMinutes} 分钟`}</p>
        <p>{movie.summary || '暂无影片简介'}</p>
        <div className="available-cinemas-notices" aria-label="影片资料来源说明">
          {notices.map((notice) => (
            <Tag color={notice.tone === 'warning' ? 'orange' : 'blue'} key={notice.id}>
              {notice.text}
            </Tag>
          ))}
        </div>
      </div>
    </section>
  );
}

function CinemaCard({ cinema, movieId }: { cinema: AvailableCinema; movieId: string }) {
  const location = useLocation();
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
          影片资料来源：{formatContentSource(cinema.contentSource)} · 更新时间：{' '}
          {formatDateTime(cinema.contentDataTime)}
        </p>
        <p className="available-cinemas-data-time">
          排期来源：{formatScheduleSource(cinema.scheduleSource)} · 更新时间：{' '}
          {formatDateTime(cinema.scheduleDataTime)}
        </p>
      </div>
      {isBusinessId(movieId) && isBusinessId(cinema.cinemaId) ? (
        <Link
          aria-label={`选择${cinema.name}的场次`}
          className="available-cinemas-purchase-link"
          to={workspacePath(
            location.pathname,
            `/shows?movieId=${encodeURIComponent(movieId)}` +
              `&cinemaId=${encodeURIComponent(cinema.cinemaId)}`,
          )}
        >
          选择影院
        </Link>
      ) : null}
    </article>
  );
}

export default function AvailableCinemasPage() {
  const { movieId } = useParams<{ movieId: string }>();
  const validMovieId = isBusinessId(movieId) ? movieId : undefined;
  const state = useAvailableCinemas(validMovieId);

  if (!validMovieId) {
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
        <p>先确认影片资料，再选择真实影院；场次和座位以进入下一页后的实时查询结果为准。</p>
      </header>

      <MovieDetailCard />
      <h2 className="available-cinemas-title">选择影院</h2>

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
            <CinemaCard cinema={cinema} key={cinema.cinemaId} movieId={validMovieId} />
          ))}
        </div>
      ) : null}
    </main>
  );
}
