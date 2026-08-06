import { Alert, Button, Empty, Skeleton } from 'antd';
import React, { useState } from 'react';
import { Link } from 'umi';

import { getFreshnessNotices } from '../../modules/content/freshness';
import { safePosterUrl } from '../../modules/content/poster';
import { useCinemaList } from '../../modules/content/useCinemaList';
import { useMovieList } from '../../modules/content/useMovieList';
import { RobotIcon } from '../../shared/components/icons/layout-icons';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import type { CinemaSummary, ContentFreshness, MovieSummary } from '../../shared/types/api';
import { AgentCard } from './AgentCard';
import './index.css';
import { PlanRecommendation } from './PlanRecommendation';

const MOVIE_SKELETON_KEYS = ['movie-loading-1', 'movie-loading-2', 'movie-loading-3'];
const CINEMA_SKELETON_KEYS = ['cinema-loading-1', 'cinema-loading-2', 'cinema-loading-3'];
const DEFAULT_CITY_NAME = '长沙';
const DEFAULT_CITY_CODE = '430100';

function FreshnessNotice({ freshness }: { freshness: ContentFreshness }) {
  const notices = getFreshnessNotices(freshness);
  return (
    <div className="home-freshness" aria-label="数据来源说明">
      {notices.map((notice) => (
        <span className={`home-freshness-item home-freshness-item--${notice.tone}`} key={notice.id}>
          {notice.text}
        </span>
      ))}
    </div>
  );
}

function HomeMovieCard({ movie }: { movie: MovieSummary }) {
  const [posterFailed, setPosterFailed] = useState(false);
  const posterUrl = safePosterUrl(movie.posterUrl);

  return (
    <article className="movie-card" data-testid={`home-movie-${movie.movieId}`}>
      {posterUrl && !posterFailed ? (
        <img
          alt={`${movie.title}海报`}
          className="movie-poster-large"
          loading="lazy"
          onError={() => setPosterFailed(true)}
          src={posterUrl}
        />
      ) : (
        <div
          className="movie-poster-large movie-poster-placeholder"
          aria-label={`${movie.title}暂无海报`}
        >
          暂无海报
        </div>
      )}
      <div className="movie-card-info">
        <h3 className="movie-card-name">{movie.title}</h3>
        <div className="movie-card-meta">
          {movie.genres.length > 0 ? movie.genres.join(' / ') : '类型待更新'}
        </div>
        <div className="home-sale-unavailable">票务信息暂不可用</div>
      </div>
    </article>
  );
}

function HomeCinemaCard({ cinema }: { cinema: CinemaSummary }) {
  return (
    <article className="cinema-card" data-testid={`home-cinema-${cinema.cinemaId}`}>
      <div className="cinema-title-wrap">
        <div className="cinema-icon-placeholder" aria-hidden="true" />
        <h3 className="cinema-name">{cinema.name}</h3>
      </div>
      <div className="cinema-address">{cinema.address?.trim() || '地址待更新'}</div>
      <div className="cinema-area">{cinema.area?.trim() || '区域待更新'}</div>
      <div className="home-sale-unavailable">票务信息暂不可用</div>
    </article>
  );
}

export default function HomePage() {
  const isMobile = useMediaQuery('(max-width: 1023px)');
  const [showPlans, setShowPlans] = useState(false);
  const movies = useMovieList({ page: 1, size: 5 });
  const cinemas = useCinemaList({ location: DEFAULT_CITY_CODE, page: 1, size: 3 });

  return (
    <div className="home-page-container">
      <h1 className="visually-hidden">妙语购票</h1>
      {isMobile && (
        <div className="home-mobile-agent-banner">
          <div className="home-mobile-agent-banner-inner">
            <div className="home-mobile-agent-banner-header">
              <div className="home-mobile-agent-icon-wrap">
                <RobotIcon size={18} />
              </div>
              <div className="home-mobile-agent-text">
                <div className="home-mobile-agent-title">妙语 AI 助理</div>
                <div className="home-mobile-agent-subtitle">告诉我你想看什么</div>
              </div>
            </div>

            <div className="home-mobile-agent-input-container">
              <input
                className="home-mobile-agent-input"
                placeholder="例如：周末有什么好看的动作片？"
              />
              <button type="button" className="home-mobile-agent-send-btn">
                发送
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="home-page-main">
        <section className="home-page-content">
          <div className="home-main-content">
            {showPlans ? (
              <PlanRecommendation onBack={() => setShowPlans(false)} />
            ) : (
              <>
                <section className="home-section" aria-labelledby="home-movies-title">
                  <div className="section-header">
                    <div>
                      <h2 className="home-section-title" id="home-movies-title">
                        正在热映
                      </h2>
                      <p className="home-section-description">仅展示内容服务返回的影片基础资料</p>
                    </div>
                    <Link className="section-link" to="/movies">
                      查看全部影片
                    </Link>
                  </div>

                  {movies.data ? <FreshnessNotice freshness={movies.data} /> : null}
                  {movies.isOfflineSnapshot ? (
                    <Alert
                      message="当前已离线，正在显示本页面内存中的影片只读快照"
                      showIcon
                      type="warning"
                    />
                  ) : null}
                  {movies.error ? (
                    <Alert
                      action={
                        <Button size="small" onClick={movies.retry}>
                          重试
                        </Button>
                      }
                      description={
                        movies.error.traceId ? `问题编号：${movies.error.traceId}` : undefined
                      }
                      message={movies.data ? '影片更新失败，已保留上次结果' : '影片加载失败'}
                      showIcon
                      type="error"
                    />
                  ) : null}
                  {movies.isRefreshing ? (
                    <div className="home-refreshing" role="status">
                      正在更新影片…
                    </div>
                  ) : null}
                  {movies.isLoading ? (
                    <div className="movies-list" aria-label="首页影片加载中">
                      {MOVIE_SKELETON_KEYS.map((key) => (
                        <div className="movie-card home-card-skeleton" key={key}>
                          <Skeleton.Image active />
                          <Skeleton active paragraph={{ rows: 2 }} title={false} />
                        </div>
                      ))}
                    </div>
                  ) : null}
                  {!movies.isLoading && !movies.error && movies.data?.records.length === 0 ? (
                    <Empty description="暂无可展示的影片" />
                  ) : null}
                  {movies.data && movies.data.records.length > 0 ? (
                    <div className="movies-list" aria-live="polite">
                      {movies.data.records.map((movie) => (
                        <HomeMovieCard key={movie.movieId} movie={movie} />
                      ))}
                    </div>
                  ) : null}
                </section>

                <section className="home-section" aria-labelledby="home-cinemas-title">
                  <div className="section-header">
                    <div>
                      <h2 className="home-section-title" id="home-cinemas-title">
                        {DEFAULT_CITY_NAME}影院
                      </h2>
                      <p className="home-section-description">不获取位置，不展示距离或距离排序</p>
                    </div>
                    <Link className="section-link" to="/cinemas">
                      查看全部影院
                    </Link>
                  </div>

                  {cinemas.data ? <FreshnessNotice freshness={cinemas.data} /> : null}
                  {cinemas.isOfflineSnapshot ? (
                    <Alert
                      message="当前已离线，正在显示本页面内存中的影院只读快照"
                      showIcon
                      type="warning"
                    />
                  ) : null}
                  {cinemas.error ? (
                    <Alert
                      action={
                        <Button size="small" onClick={cinemas.retry}>
                          重试
                        </Button>
                      }
                      description={
                        cinemas.error.traceId ? `问题编号：${cinemas.error.traceId}` : undefined
                      }
                      message={cinemas.data ? '影院更新失败，已保留上次结果' : '影院加载失败'}
                      showIcon
                      type="error"
                    />
                  ) : null}
                  {cinemas.isRefreshing ? (
                    <div className="home-refreshing" role="status">
                      正在更新影院…
                    </div>
                  ) : null}
                  {cinemas.isLoading ? (
                    <div className="cinemas-list" aria-label="首页影院加载中">
                      {CINEMA_SKELETON_KEYS.map((key) => (
                        <div className="cinema-card home-card-skeleton" key={key}>
                          <Skeleton active paragraph={{ rows: 3 }} title={{ width: '65%' }} />
                        </div>
                      ))}
                    </div>
                  ) : null}
                  {!cinemas.isLoading && !cinemas.error && cinemas.data?.records.length === 0 ? (
                    <Empty description="长沙暂无可展示的影院" />
                  ) : null}
                  {cinemas.data && cinemas.data.records.length > 0 ? (
                    <div className="cinemas-list" aria-live="polite">
                      {cinemas.data.records.map((cinema) => (
                        <HomeCinemaCard cinema={cinema} key={cinema.cinemaId} />
                      ))}
                    </div>
                  ) : null}
                </section>

                <div className="personalized-banner">
                  <div className="personalized-icon-wrap">
                    <RobotIcon size={24} />
                  </div>
                  <div className="personalized-content">
                    <div className="personalized-title">想要更精准的推荐？</div>
                    <div className="personalized-desc">
                      开启后，AI将结合路线、出发时间与附近美食等信息，为你提供个性化观影方案
                    </div>
                  </div>
                  <button type="button" className="personalized-btn">
                    开启个性化服务
                  </button>
                </div>
              </>
            )}
          </div>
        </section>

        {!isMobile && (
          <aside className="home-page-agent-sidebar">
            <AgentCard onViewPlan={() => setShowPlans(true)} />
          </aside>
        )}
      </div>
    </div>
  );
}
