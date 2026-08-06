import { Alert, Button, Empty, Input, Pagination, Skeleton } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'umi';

import {
  buildCinemaListSearchParams,
  parseCinemaListQuery,
  type NormalizedCinemaListQuery,
} from '../../modules/content/cinemaListQuery';
import { getFreshnessNotices } from '../../modules/content/freshness';
import { useCinemaList } from '../../modules/content/useCinemaList';
import type { CinemaSummary } from '../../shared/types/api';
import './index.css';

const { Search } = Input;
const SKELETON_KEYS = Array.from({ length: 4 }, (_, index) => `cinema-skeleton-${index + 1}`);

function displayText(value: string | null, fallback: string): string {
  const normalized = value?.trim();
  return normalized ? normalized : fallback;
}

/** 渲染后端影院摘要，并使用真实业务 ID 进入公开详情页。 */
function CinemaCard({ cinema }: { cinema: CinemaSummary }) {
  const area = displayText(cinema.area, '区域待更新');
  const address = displayText(cinema.address, '地址待更新');
  const logoText = cinema.name.trim().slice(0, 1) || '影';

  return (
    <article data-testid={`cinema-${cinema.cinemaId}`}>
      <Link className="cinema-list-item" to={`/cinemas/${encodeURIComponent(cinema.cinemaId)}`}>
        <div className="cinema-list-logo" aria-hidden="true">
          {logoText}
        </div>
        <div className="cinema-list-center">
          <h2 className="cinema-list-title">{cinema.name}</h2>
          <div className="cinema-list-meta">
            <span className="cinema-list-area">{area}</span>
            <span className="cinema-list-city">城市代码 {cinema.cityCode ?? '待更新'}</span>
          </div>
          <p className="cinema-list-address">
            <span aria-hidden="true">📍</span>
            {address}
          </p>
        </div>
        <span className="cinema-list-arrow" aria-hidden="true">
          ›
        </span>
      </Link>
    </article>
  );
}

/** `/cinemas` 路由页面：从 URL 恢复条件并组合 content 模块查询状态。 */
export default function CinemasPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const searchParamsKey = searchParams.toString();
  const parsedQuery = useMemo(
    () => parseCinemaListQuery(new URLSearchParams(searchParamsKey)),
    [searchParamsKey],
  );
  const { query } = parsedQuery;
  const { data, error, isLoading, isOfflineSnapshot, isRefreshing, retry } = useCinemaList(query);
  const [keywordDraft, setKeywordDraft] = useState(query.keyword ?? '');

  useEffect(() => setKeywordDraft(query.keyword ?? ''), [query.keyword]);

  const updateQuery = (patch: Partial<NormalizedCinemaListQuery>) => {
    setSearchParams(buildCinemaListSearchParams({ ...query, ...patch }));
  };

  const freshnessNotices = data ? getFreshnessNotices(data) : [];

  return (
    <div className="cinemas-page-container">
      <main className="cinemas-page-main">
        <section className="cinemas-page-content">
          <nav className="cinemas-breadcrumb" aria-label="面包屑">
            首页 / <span className="current">影院</span>
          </nav>

          <header className="cinemas-heading-row">
            <div>
              <h1>影院列表</h1>
              <p>当前城市代码：{query.location}。影院基础信息来自后端内容服务。</p>
            </div>
            <Search
              allowClear
              aria-label="搜索影院名称或地址"
              enterButton="搜索"
              maxLength={100}
              onChange={(event) => setKeywordDraft(event.target.value)}
              onSearch={(value) => updateQuery({ keyword: value.trim() || undefined, page: 1 })}
              placeholder="输入影院名称、区域或地址"
              value={keywordDraft}
            />
          </header>

          {parsedQuery.issues.length > 0 ? (
            <Alert
              className="cinemas-status-alert"
              message="部分地址参数无效，已使用安全默认值"
              description={parsedQuery.issues.join('；')}
              showIcon
              type="warning"
            />
          ) : null}

          {freshnessNotices.length > 0 ? (
            <div className="cinemas-freshness" aria-label="数据来源说明">
              {freshnessNotices.map((notice) => (
                <span
                  className={`cinemas-freshness-item cinemas-freshness-item--${notice.tone}`}
                  key={notice.id}
                >
                  {notice.text}
                </span>
              ))}
            </div>
          ) : null}

          {isOfflineSnapshot ? (
            <Alert
              className="cinemas-status-alert"
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
              className="cinemas-status-alert"
              description={error.traceId ? `问题编号：${error.traceId}` : undefined}
              message={data ? '刷新失败，已保留上次加载的影院' : '影院加载失败'}
              showIcon
              type="error"
            />
          ) : null}

          {isRefreshing ? <div className="cinemas-refreshing">正在更新影院列表…</div> : null}

          {isLoading ? (
            <div className="cinemas-list-view" aria-label="影院加载中">
              {SKELETON_KEYS.map((key) => (
                <div className="cinema-list-item cinema-list-item--skeleton" key={key}>
                  <Skeleton.Avatar active shape="square" size={72} />
                  <Skeleton active paragraph={{ rows: 2 }} title={{ width: '55%' }} />
                </div>
              ))}
            </div>
          ) : null}

          {!isLoading && !error && data?.records.length === 0 ? (
            <Empty description="没有找到符合条件的影院" />
          ) : null}

          {data && data.records.length > 0 ? (
            <>
              <div className="cinemas-list-view" aria-live="polite">
                {data.records.map((cinema) => (
                  <CinemaCard cinema={cinema} key={cinema.cinemaId} />
                ))}
              </div>
              <div className="cinemas-pagination">
                <span>共 {data.total} 家影院</span>
                <Pagination
                  current={data.page}
                  onChange={(page, size) =>
                    updateQuery({ page: size === query.size ? page : 1, size })
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
      </main>
    </div>
  );
}
