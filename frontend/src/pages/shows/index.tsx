import React from 'react';
import { history, useSearchParams } from 'umi';
import { Button, Spin, Alert } from 'antd';
import { useShows } from '../../modules/ticketing/hooks';
import './index.css';

/**
 * 场次选择页面：/shows?movieId={movieId}&cinemaId={cinemaId}
 * 只查询未来可售场次并做展现，无数据时显式渲染空状态提示。
 */
export default function ShowsPage() {
  const [searchParams] = useSearchParams();
  const movieId = searchParams.get('movieId') || undefined;
  const cinemaId = searchParams.get('cinemaId') || undefined;

  const { loading, shows, error, isEmpty, refetch } = useShows(movieId, cinemaId);

  if (!movieId || !cinemaId) {
    return (
      <div className="shows-page-container">
        <Alert
          type="warning"
          showIcon
          message="参数错误"
          description="请携带完整的 movieId 与 cinemaId 参数访问场次列表页。"
        />
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
      <div className="shows-page-header">
        <h1 className="shows-page-title">选择场次</h1>
        <div className="shows-cinema-sub">{cinemaName}</div>
      </div>

      {error && (
        <Alert
          type="error"
          showIcon
          message="查询可售场次发生异常"
          description={error.message || '请检查网络配置或刷新重试'}
          action={
            <Button size="small" onClick={refetch}>
              重试
            </Button>
          }
          className="shows-alert"
        />
      )}

      {loading ? (
        <div className="shows-loading">
          <Spin tip="拉取场次列表中..." />
        </div>
      ) : isEmpty ? (
        <div className="shows-empty" role="status">
          当前影片和影院暂无可售场次
        </div>
      ) : (
        <div className="shows-list">
          {shows.map((show) => (
            <div key={show.showId} className="show-card">
              <div className="show-time-group">
                <span className="show-start-time">
                  {new Date(show.startTime).toLocaleTimeString('zh-CN', {
                    hour: '2-digit',
                    minute: '2-digit',
                    hour12: false,
                  })}
                </span>
                <span className="show-end-time">
                  散场{' '}
                  {new Date(show.endTime).toLocaleTimeString('zh-CN', {
                    hour: '2-digit',
                    minute: '2-digit',
                    hour12: false,
                  })}
                </span>
              </div>

              <div className="show-info-group">
                <span className="show-auditorium">{show.auditoriumName}</span>
                <span className="show-version">{show.languageVersion}</span>
              </div>

              <div className="show-price-group">
                <div>
                  <span className="show-price-unit">¥</span>
                  <span className="show-price">{show.basePrice}</span>
                </div>
              </div>

              <Button
                type="primary"
                onClick={() => handleSelectShow(show.showId)}
                aria-label={`选择 ${show.auditoriumName} ${show.startTime} 场次去选座`}
              >
                去选座
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
