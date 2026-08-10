import { Alert, Button, Empty, Skeleton, Tag } from 'antd';
import React, { useState } from 'react';
import { Link, useParams } from 'umi';

import {
  formatContentSource,
  formatScheduleSource,
  getFreshnessNotices,
} from '../../modules/content/freshness';
import { useCinemaDetail } from '../../modules/content/useCinemaDetail';
import { useAvailableMovies } from '../../modules/ticketing/useAvailableMovies';
import type { AvailableMovie } from '../../modules/ticketing/types';
import './detail.css';

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function safePosterUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value, window.location.origin);
    return url.protocol === 'https:' || url.origin === window.location.origin ? url.href : null;
  } catch {
    return null;
  }
}

function isDemoSchedule(source: string): boolean {
  return source.toLowerCase() === 'demo-seed' || source.toUpperCase() === 'MOCK';
}

function AvailableMovieCard({ cinemaId, movie }: { cinemaId: string; movie: AvailableMovie }) {
  const [posterFailed, setPosterFailed] = useState(false);
  const posterUrl = posterFailed ? null : safePosterUrl(movie.posterUrl);
  return (
    <article className="cinema-detail-movie-card">
      <div className="cinema-detail-poster">
        {posterUrl ? (
          <img
            alt={`${movie.title}海报`}
            loading="lazy"
            onError={() => setPosterFailed(true)}
            src={posterUrl}
          />
        ) : (
          <span aria-label={`${movie.title}暂无海报`}>暂无海报</span>
        )}
      </div>
      <div className="cinema-detail-movie-main">
        <div className="cinema-detail-movie-heading">
          <h3>{movie.title}</h3>
          {isDemoSchedule(movie.scheduleSource) ? <Tag color="purple">演示排期</Tag> : null}
        </div>
        <p>{movie.showCount} 个可选场次</p>
        <p>最近开场：{formatDateTime(movie.nearestStartTime)}</p>
        <p className="cinema-detail-data-time">
          影片资料来源：{formatContentSource(movie.contentSource)} · 更新时间：
          {formatDateTime(movie.contentDataTime)}
        </p>
        <p className="cinema-detail-data-time">
          排期来源：{formatScheduleSource(movie.scheduleSource)} · 更新时间：
          {formatDateTime(movie.scheduleDataTime)}
        </p>
      </div>
      <Link
        className="cinema-detail-purchase-link"
        to={`/shows?movieId=${encodeURIComponent(movie.movieId)}&cinemaId=${encodeURIComponent(cinemaId)}`}
      >
        选择影片
      </Link>
    </article>
  );
}

/** 影院详情页分别呈现内容资料和票务排期状态，任一失败都不伪造另一块数据。 */
export default function CinemaDetailPage() {
  const { cinemaId } = useParams<{ cinemaId: string }>();
  const cinemaState = useCinemaDetail(cinemaId);
  const scheduleState = useAvailableMovies(cinemaId);

  if (!cinemaId) {
    return <Alert type="error" showIcon message="影院地址无效" />;
  }

  if (cinemaState.isLoading) {
    return (
      <div className="cinema-detail-page">
        <Skeleton active paragraph={{ rows: 5 }} />
      </div>
    );
  }

  if (cinemaState.error) {
    const notFound = cinemaState.error.status === 404;
    return (
      <div className="cinema-detail-page">
        <Alert
          type={notFound ? 'warning' : 'error'}
          showIcon
          message={notFound ? '影院不存在或已下线' : '影院详情加载失败'}
          description={
            cinemaState.error.traceId ? `问题编号：${cinemaState.error.traceId}` : undefined
          }
          action={notFound ? undefined : <Button onClick={cinemaState.retry}>重试</Button>}
        />
        <Link className="cinema-detail-back" to="/cinemas">
          返回影院列表
        </Link>
      </div>
    );
  }

  const cinema = cinemaState.data;
  if (!cinema) return null;
  const notices = getFreshnessNotices(cinema);

  return (
    <main className="cinema-detail-page">
      <nav aria-label="面包屑" className="cinema-detail-breadcrumb">
        <Link to="/cinemas">影院列表</Link>
        <span aria-hidden="true"> / </span>
        <span>{cinema.name}</span>
      </nav>
      <section className="cinema-detail-hero">
        <div className="cinema-detail-logo" aria-hidden="true">
          {cinema.name.trim().slice(0, 1) || '影'}
        </div>
        <div>
          <h1>{cinema.name}</h1>
          <p>
            {cinema.area || '区域待更新'} · 城市代码 {cinema.cityCode || '待更新'}
          </p>
          <address>{cinema.address || '地址待更新'}</address>
        </div>
      </section>
      <div className="cinema-detail-freshness" aria-label="影院资料来源">
        {notices.map((notice) => (
          <Tag color={notice.tone === 'warning' ? 'orange' : 'blue'} key={notice.id}>
            {notice.text}
          </Tag>
        ))}
      </div>

      <section aria-labelledby="available-movies-title" className="cinema-detail-schedules">
        <div className="cinema-detail-section-title">
          <div>
            <h2 id="available-movies-title">选择影片</h2>
            <p>未来可售演示排期，场次和座位以后续页面实时查询为准。</p>
          </div>
        </div>
        {scheduleState.isLoading ? <Skeleton active paragraph={{ rows: 4 }} /> : null}
        {scheduleState.error ? (
          <Alert
            action={<Button onClick={scheduleState.retry}>重新加载排期</Button>}
            description={
              scheduleState.error.traceId ? `问题编号：${scheduleState.error.traceId}` : undefined
            }
            message="影院资料已加载，但排期暂时不可用"
            showIcon
            type="error"
          />
        ) : null}
        {!scheduleState.isLoading && !scheduleState.error && scheduleState.movies.length === 0 ? (
          <Empty description="未来暂无可售影片" />
        ) : null}
        {scheduleState.movies.length > 0 ? (
          <div className="cinema-detail-movie-list">
            {scheduleState.movies.map((movie) => (
              <AvailableMovieCard cinemaId={cinemaId} key={movie.movieId} movie={movie} />
            ))}
          </div>
        ) : null}
      </section>
    </main>
  );
}
