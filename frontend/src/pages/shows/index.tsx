import React, { useMemo } from 'react';
import { history, Link, useSearchParams } from 'umi';
import { Alert } from 'antd';
import { ErrorBlock } from 'antd-mobile';
import { ShowList } from '../../features/show-list/ShowList';
import type { ShowItemUI } from '../../features/show-list/ShowList';
import { useShows } from '../../modules/ticketing/hooks';
import { formatShowDate, formatShowTime } from '../../modules/ticketing/formatters';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

/**
 * 场次选择页面：/shows?movieId={movieId}&cinemaId={cinemaId}
 * 只查询未来可售场次并做展现，无数据时显式渲染空状态提示。
 */
export default function ShowsPage() {
  const [searchParams] = useSearchParams();
  const movieId = searchParams.get('movieId') || undefined;
  const cinemaId = searchParams.get('cinemaId') || undefined;
  const isMobile = useMediaQuery('(max-width: 1023px)');

  const { loading, shows, error, refetch } = useShows(movieId, cinemaId);

  const showItems = useMemo<ShowItemUI[]>(
    () =>
      shows.map(({ startTime, endTime, ...show }) => ({
        ...show,
        showDateText: formatShowDate(startTime),
        startTimeText: formatShowTime(startTime),
        endTimeText: formatShowTime(endTime),
      })),
    [shows],
  );

  if (!movieId || !cinemaId) {
    return (
      <div className="shows-page-container">
        {isMobile ? (
          <ErrorBlock
            status="default"
            title="参数错误"
            description="请携带完整的 movieId 与 cinemaId 参数访问场次列表页。"
          />
        ) : (
          <Alert
            type="warning"
            showIcon
            message="参数错误"
            description="请携带完整的 movieId 与 cinemaId 参数访问场次列表页。"
          />
        )}
      </div>
    );
  }

  const handleSelectShow = (showId: string) => {
    history.push(
      `/shows/${encodeURIComponent(showId)}/seats?movieId=${encodeURIComponent(movieId)}&cinemaId=${encodeURIComponent(cinemaId)}`,
    );
  };

  const cinemaName = shows.length > 0 ? shows[0].cinemaName : '加载中...';

  return (
    <div className="shows-page-container">
      <nav aria-label="面包屑" className="shows-breadcrumb">
        <Link to="/cinemas">影院列表</Link>
        <span aria-hidden="true"> / </span>
        <span>{cinemaName}</span>
        <span aria-hidden="true"> / </span>
        <span className="current">选择场次</span>
      </nav>
      <div className="shows-page-header">
        <h1 className="shows-page-title">选择场次</h1>
        <div className="shows-cinema-sub">{cinemaName}</div>
      </div>

      <ShowList
        shows={showItems}
        loading={loading}
        error={error}
        onRetry={refetch}
        onSelectShow={handleSelectShow}
      />
    </div>
  );
}
