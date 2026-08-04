import { Alert, Button, Empty, Input, Pagination, Skeleton } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'umi';

import { getFreshnessNotices } from '../../modules/content/freshness';
import {
  buildMovieListSearchParams,
  parseMovieListQuery,
  type NormalizedMovieListQuery,
} from '../../modules/content/movieListQuery';
import { useMovieList } from '../../modules/content/useMovieList';
import type { MovieSummary } from '../../shared/types/api';
import './index.css';

const { Search } = Input;
// 后端目前支持 genre 精确匹配，但没有“可用类型列表”接口；本期只展示产品原型已确认且真实数据中存在的常用类型。
// 不从当前分页 records 临时汇总类型，否则用户会误以为其他页不存在更多类型。
const MOVIE_GENRES = ['动作', '喜剧', '爱情', '科幻', '动画', '悬疑', '剧情'] as const;
const SKELETON_KEYS = Array.from({ length: 10 }, (_, index) => `movie-skeleton-${index + 1}`);

/**
 * 只允许同源图片或 HTTPS 海报。
 *
 * Provider 返回的 URL 属于外部输入；HTTP 外链会造成混合内容风险，非法地址统一使用本地占位，不尝试加载。
 */
function safePosterUrl(posterUrl: string | null): string | null {
  if (!posterUrl) {
    return null;
  }

  try {
    const url = new URL(posterUrl, window.location.origin);
    if (url.protocol !== 'https:' && url.origin !== window.location.origin) {
      return null;
    }
    return url.href;
  } catch {
    return null;
  }
}

/** 渲染后端影片摘要；卡片本期不承担详情跳转，避免把尚未实现的详情路由做成可点击入口。 */
function MovieCard({ movie }: { movie: MovieSummary }) {
  const posterUrl = safePosterUrl(movie.posterUrl);
  return (
    <article className="movie-grid-card" data-testid={`movie-${movie.movieId}`}>
      <div className="movie-grid-poster">
        {posterUrl ? (
          <img alt={`${movie.title}海报`} loading="lazy" src={posterUrl} />
        ) : (
          <span className="movie-grid-poster-placeholder" aria-label={`${movie.title}暂无海报`}>
            暂无海报
          </span>
        )}
      </div>
      <div className="movie-grid-info">
        <h2 className="movie-grid-name" title={movie.title}>
          {movie.title}
        </h2>
        <div className="movie-grid-score-row">
          <span className="movie-grid-score-icon" aria-hidden="true">
            ★
          </span>
          <span className="movie-grid-score">
            {movie.rating === null ? '暂无评分' : movie.rating.toFixed(1)}
          </span>
        </div>
        <div className="movie-grid-tags" aria-label="影片类型">
          {movie.genres.length > 0 ? (
            movie.genres.map((genre) => (
              <span key={`${movie.movieId}-${genre}`} className="movie-tag">
                {genre}
              </span>
            ))
          ) : (
            <span className="movie-tag">类型待更新</span>
          )}
        </div>
        <div className="movie-grid-desc">
          {movie.durationMinutes === null ? '时长待更新' : `${movie.durationMinutes} 分钟`}
        </div>
      </div>
    </article>
  );
}

/** `/movies` 路由页面：读取 URL、组合 content 模块状态并渲染桌面/移动共用视图。 */
export default function MoviesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  // 依赖序列化字符串而不是 URLSearchParams 对象引用，确保浏览器前进/后退时能重新解析真实条件。
  const searchParamsKey = searchParams.toString();
  const parsedQuery = useMemo(
    () => parseMovieListQuery(new URLSearchParams(searchParamsKey)),
    [searchParamsKey],
  );
  const { query } = parsedQuery;
  const { data, error, isLoading, isOfflineSnapshot, isRefreshing, retry } = useMovieList(query);
  const [keywordDraft, setKeywordDraft] = useState(query.keyword ?? '');

  useEffect(() => {
    setKeywordDraft(query.keyword ?? '');
  }, [query.keyword]);

  /** 只更新后端已支持的查询条件；默认值由构造器省略，筛选变化时调用方显式把 page 重置为 1。 */
  const updateQuery = (patch: Partial<NormalizedMovieListQuery>) => {
    const nextQuery: NormalizedMovieListQuery = { ...query, ...patch };
    setSearchParams(buildMovieListSearchParams(nextQuery));
  };

  // 来源属于整次分页查询，不复制到每张卡片，避免同一页出现互相矛盾的来源提示。
  const freshnessNotices = data ? getFreshnessNotices(data) : [];

  return (
    <div className="movies-page-container">
      <div className="movies-page-main">
        <section className="movies-page-content">
          <nav className="movies-breadcrumb" aria-label="面包屑">
            首页 / <span className="current">电影</span>
          </nav>

          <div className="movies-heading-row">
            <div>
              <h1>影片列表</h1>
              <p>影片信息来自后端内容服务，演示或降级数据会明确标注。</p>
            </div>
            <Search
              allowClear
              aria-label="搜索影片名称"
              enterButton="搜索"
              maxLength={100}
              onChange={(event) => setKeywordDraft(event.target.value)}
              onSearch={(value) => updateQuery({ keyword: value.trim() || undefined, page: 1 })}
              placeholder="输入影片名称"
              value={keywordDraft}
            />
          </div>

          <div className="movies-filters-bar" aria-label="影片类型筛选">
            <button
              type="button"
              className={`filter-pill${query.genre ? '' : ' active'}`}
              aria-pressed={!query.genre}
              onClick={() => updateQuery({ genre: undefined, page: 1 })}
            >
              全部
            </button>
            {MOVIE_GENRES.map((genre) => (
              <button
                type="button"
                className={`filter-pill${query.genre === genre ? ' active' : ''}`}
                aria-pressed={query.genre === genre}
                key={genre}
                onClick={() => updateQuery({ genre, page: 1 })}
              >
                {genre}
              </button>
            ))}
          </div>

          {parsedQuery.issues.length > 0 ? (
            <Alert
              className="movies-status-alert"
              message="部分地址参数无效，已使用安全默认值"
              description={parsedQuery.issues.join('；')}
              showIcon
              type="warning"
            />
          ) : null}

          {freshnessNotices.length > 0 ? (
            <div className="movies-freshness" aria-label="数据来源说明">
              {freshnessNotices.map((notice) => (
                <span
                  className={`movies-freshness-item movies-freshness-item--${notice.tone}`}
                  key={notice.id}
                >
                  {notice.text}
                </span>
              ))}
            </div>
          ) : null}

          {isOfflineSnapshot ? (
            <Alert
              className="movies-status-alert"
              message="当前已离线，正在显示本页面内存中的只读快照"
              showIcon
              type="warning"
            />
          ) : null}

          {error ? (
            <Alert
              action={
                <Button size="small" onClick={retry}>
                  重试
                </Button>
              }
              className="movies-status-alert"
              description={error.traceId ? `问题编号：${error.traceId}` : undefined}
              message={data ? '刷新失败，已保留上次加载的影片' : '影片加载失败'}
              showIcon
              type="error"
            />
          ) : null}

          {isRefreshing ? <div className="movies-refreshing">正在更新影片列表…</div> : null}

          {isLoading ? (
            <div className="movies-grid-view" aria-label="影片加载中">
              {SKELETON_KEYS.map((key) => (
                <div className="movie-grid-card movie-grid-card--skeleton" key={key}>
                  <Skeleton.Image active />
                  <Skeleton active paragraph={{ rows: 2 }} title={{ width: '70%' }} />
                </div>
              ))}
            </div>
          ) : null}

          {!isLoading && !error && data?.records.length === 0 ? (
            <Empty description="没有找到符合条件的影片" />
          ) : null}

          {data && data.records.length > 0 ? (
            <>
              <div className="movies-grid-view" aria-live="polite">
                {data.records.map((movie) => (
                  <MovieCard key={movie.movieId} movie={movie} />
                ))}
              </div>
              <div className="movies-pagination">
                <span>共 {data.total} 部影片</span>
                <Pagination
                  current={data.page}
                  onChange={(page, size) =>
                    updateQuery({
                      // 改变每页数量后原页码可能越界，因此回到第一页；单纯翻页则保留目标页码。
                      page: size === query.size ? page : 1,
                      size,
                    })
                  }
                  pageSize={data.size}
                  pageSizeOptions={['10', '20', '50']}
                  showSizeChanger
                  total={data.total}
                />
              </div>
            </>
          ) : null}
        </section>
      </div>
    </div>
  );
}
