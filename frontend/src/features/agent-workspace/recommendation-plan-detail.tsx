import { Alert, Button, Empty, Skeleton, Tag } from 'antd';
import React from 'react';
import { Link } from 'umi';

import { safePosterUrl } from '../../modules/content/poster';
import { useCinemaDetail } from '../../modules/content/useCinemaDetail';
import { useMovieDetail } from '../../modules/content/useMovieDetail';
import type { AgentPlanDisplay } from '../../modules/agent/projection';
import { buildAgentSelectSeatsPath } from '../../modules/agent/projection';
import { useShows } from '../../modules/ticketing/hooks';
import { CinemaLocationMap } from './cinema-location-map';

interface RecommendationPlanDetailProps {
  plan: AgentPlanDisplay;
  sessionId: string;
  workspaceBase: string;
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '时间待确认';
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function isCurrentOrFutureShow(value: string): boolean {
  const timestamp = Date.parse(value);
  return Number.isNaN(timestamp) || timestamp > Date.now();
}

function formatPlanReason(value: string): string {
  const labels: Record<string, string> = {
    COMPREHENSIVE: '综合条件更均衡',
    LOW_PRICE: '当前价格更低',
    EARLY_TIME: '开场时间更早',
    TIME_FIRST: '开场时间更合适',
    NEAREST: '距离影院更近',
    '固定推荐结果': '符合当前可购条件',
  };
  const key = Object.keys(labels).find((candidate) => value.includes(candidate));
  return key ? labels[key] : value;
}

/**
 * 将已选推荐方案展开为购票前的说明页。
 *
 * 详情页只读取内容和实时场次；用户点击后仍进入既有选座页，座位上限、锁座与建单规则不在此重写。
 */
export function RecommendationPlanDetail({
  plan,
  sessionId,
  workspaceBase,
}: RecommendationPlanDetailProps) {
  const movie = useMovieDetail(plan.movieId);
  const cinema = useCinemaDetail(plan.cinemaId);
  const shows = useShows(plan.movieId, plan.cinemaId);
  const posterUrl = safePosterUrl(movie.data?.posterUrl ?? null);
  const sessionWorkspaceBase = `${workspaceBase}/${encodeURIComponent(sessionId)}`;
  const selectedShowPath = `${sessionWorkspaceBase}${buildAgentSelectSeatsPath(
    plan.showId,
    plan.movieId,
    plan.cinemaId,
  )}`;
  const alternativeShows = shows.shows
    .filter((show) => show.showId !== plan.showId && show.status === 'ON_SALE')
    .filter((show) => isCurrentOrFutureShow(show.startTime))
    .slice(0, 4);

  return (
    <main className="agent-plan-detail" aria-labelledby="agent-plan-detail-title">
      <header className="agent-task-header">
        <Link className="agent-task-back" to={sessionWorkspaceBase}>
          返回方案
        </Link>
        <nav className="agent-task-steps" aria-label="购票步骤">
          <span className="is-current">方案详情</span>
          <span>选座</span>
          <span>确认订单</span>
          <span>支付</span>
        </nav>
      </header>

      <section className="agent-plan-detail-hero">
        <div className="agent-plan-detail-poster">
          {movie.isLoading ? (
            <Skeleton.Image active className="agent-plan-detail-poster-skeleton" />
          ) : posterUrl ? (
            <img alt={`${movie.data?.title ?? plan.movieName}海报`} src={posterUrl} />
          ) : (
            <div className="agent-plan-detail-poster-fallback" aria-label="暂无影片海报">
              暂无海报
            </div>
          )}
        </div>
        <div className="agent-plan-detail-summary">
          <Tag color="purple">{plan.planType === 'LOW_PRICE' ? '低价优先' : '推荐方案'}</Tag>
          <h1 id="agent-plan-detail-title">{movie.data?.title ?? plan.movieName}</h1>
          {movie.isLoading ? (
            <Skeleton active paragraph={{ rows: 3 }} />
          ) : (
            <>
              <p className="agent-plan-detail-meta">
                {movie.data?.genres.length ? movie.data.genres.join(' / ') : '类型待更新'}
                {movie.data?.durationMinutes !== null && movie.data?.durationMinutes !== undefined
                  ? ` · ${movie.data.durationMinutes} 分钟`
                  : ''}
              </p>
              {movie.data?.rating !== null && movie.data?.rating !== undefined && (
                <p className="agent-plan-detail-rating">评分 {movie.data.rating}</p>
              )}
              <p className="agent-plan-detail-description">
                {movie.data?.summary || '影片简介暂不可用，请以影院实际放映信息为准。'}
              </p>
            </>
          )}
        </div>
      </section>

      {movie.error && (
        <Alert
          action={<Button onClick={movie.retry}>重试</Button>}
          message="影片资料暂不可用"
          description="已保留当前推荐方案；可稍后重试获取海报、类型和影片简介。"
          showIcon
          type="warning"
        />
      )}

      <section
        className="agent-plan-detail-section agent-plan-detail-show"
        aria-labelledby="agent-plan-show-title"
      >
        <div>
          <span className="agent-section-eyebrow">已选场次</span>
          <h2 id="agent-plan-show-title">{cinema.data?.name ?? plan.cinemaName}</h2>
          <p>{formatDateTime(plan.startTime)}</p>
          {cinema.isLoading ? (
            <Skeleton active paragraph={{ rows: 1 }} title={false} />
          ) : (
            <p>{cinema.data?.address || '该影院暂未维护地址'}</p>
          )}
        </div>
        <div className="agent-plan-detail-price">
          <strong>
            {plan.currency === 'CNY' ? '¥' : `${plan.currency} `}
            {plan.price}
          </strong>
          <span>最终价格以建单页为准</span>
        </div>
      </section>

      {cinema.error && (
        <Alert
          action={<Button onClick={cinema.retry}>重试</Button>}
          message="影院资料暂不可用"
          description="已保留已选场次；可稍后重试获取影院地址。地图只在影院地址可用时显示。"
          showIcon
          type="warning"
        />
      )}

      <section className="agent-plan-detail-section" aria-labelledby="agent-plan-reasons-title">
        <span className="agent-section-eyebrow">推荐理由</span>
        <h2 id="agent-plan-reasons-title">为什么推荐这个方案</h2>
        {plan.reasons.length > 0 ? (
          <ul className="agent-plan-detail-reasons">
            {plan.reasons.map((reason) => (
              <li key={reason}>{formatPlanReason(reason)}</li>
            ))}
          </ul>
        ) : (
          <p>当前方案符合你已表达的观影条件。</p>
        )}
      </section>

      <section className="agent-plan-location" aria-labelledby="agent-plan-location-title">
        <CinemaLocationMap
          cinemaName={cinema.data?.name ?? plan.cinemaName}
          latitude={cinema.data?.latitude}
          longitude={cinema.data?.longitude}
          sessionId={sessionId}
        />
        <div>
          <span className="agent-section-eyebrow">位置与距离</span>
          <h2 id="agent-plan-location-title">影院位置</h2>
          {cinema.isLoading ? (
            <Skeleton active paragraph={{ rows: 2 }} title={false} />
          ) : (
            <p>{cinema.data?.address || '该影院暂未维护地址，暂时不能展示地图和距离。'}</p>
          )}
          <p className="agent-plan-location-note">
            {cinema.data?.address
              ? '进入方案详情后会申请浏览器定位，自动计算你与影院的直线距离；不会保存到会话或订单。'
              : '补充影院地址后，才能查看地图和距离。'}
          </p>
        </div>
      </section>

      <section
        className="agent-plan-detail-section"
        aria-labelledby="agent-plan-alternatives-title"
      >
        <span className="agent-section-eyebrow">可替换场次</span>
        <h2 id="agent-plan-alternatives-title">同影院的其他可售场次</h2>
        {shows.loading ? <Skeleton active paragraph={{ rows: 2 }} /> : null}
        {!shows.loading && shows.error && (
          <Alert showIcon message="场次暂不可查询" type="warning" />
        )}
        {!shows.loading && !shows.error && alternativeShows.length === 0 && (
          <Empty description="暂无可替换场次" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
        {alternativeShows.length > 0 && (
          <div className="agent-plan-alternative-list">
            {alternativeShows.map((show) => (
              <Link
                className="agent-plan-alternative"
                key={show.showId}
                to={`${sessionWorkspaceBase}${buildAgentSelectSeatsPath(show.showId, show.movieId, show.cinemaId)}`}
              >
                <span>{formatDateTime(show.startTime)}</span>
                <span>
                  {show.auditoriumName} · {show.languageVersion}
                </span>
                <strong>¥{show.basePrice}</strong>
              </Link>
            ))}
          </div>
        )}
      </section>

      <footer className="agent-plan-detail-actions">
        <Link className="agent-plan-detail-primary" to={selectedShowPath}>
          选择此场次并选座
        </Link>
        <p>进入选座后会重新查询座位图，最多可选 6 张票。</p>
      </footer>
    </main>
  );
}
