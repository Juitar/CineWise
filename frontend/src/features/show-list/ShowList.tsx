import React from 'react';
import { Button, Spin, Empty, Result } from 'antd';
import { Button as MobileButton, SpinLoading, ErrorBlock } from 'antd-mobile';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import type { ShowSummary } from '../../modules/ticketing/types';
import './index.css';

export type ShowItemUI = Omit<ShowSummary, 'startTime' | 'endTime'> & {
  startTimeText: string;
  endTimeText: string;
};

export interface ShowListProps {
  /** 场次数据列表 */
  shows: ShowItemUI[];
  /** 是否正在加载 */
  loading?: boolean;
  /** 错误对象 */
  error?: Error | null;
  /** 选座回调 */
  onSelectShow?: (showId: string) => void;
  /** 查询失败后的只读重试回调 */
  onRetry?: () => void;
}

/**
 * 场次列表纯展示组件
 * 负责展示开场/散场时间、影厅、语言版本、票价、余座及场次状态
 * 纯粹消费 props，不发请求，不自动获取或组装 ID
 */
export const ShowList: React.FC<ShowListProps> = ({
  shows,
  loading,
  error,
  onSelectShow,
  onRetry,
}) => {
  const isMobile = useMediaQuery('(max-width: 1023px)');

  if (loading) {
    return (
      <div className="show-list-loading">
        {isMobile ? <SpinLoading color="primary" /> : <Spin size="large" />}
      </div>
    );
  }

  if (error) {
    return (
      <div className="show-list-error">
        {isMobile ? (
          <div className="show-list-error-content">
            <ErrorBlock status="default" description={error.message || '加载场次失败'} />
            {onRetry && (
              <MobileButton color="primary" onClick={onRetry} className="show-list-retry-btn">
                重新加载
              </MobileButton>
            )}
          </div>
        ) : (
          <Result
            status="error"
            title="加载场次失败"
            subTitle={error.message}
            extra={
              onRetry ? (
                <Button type="primary" onClick={onRetry} className="show-list-retry-btn">
                  重新加载
                </Button>
              ) : undefined
            }
          />
        )}
      </div>
    );
  }

  if (!shows || shows.length === 0) {
    return (
      <div className="show-list-empty">
        {isMobile ? (
          <ErrorBlock status="empty" description="暂无可售场次" />
        ) : (
          <Empty description="暂无可售场次" />
        )}
      </div>
    );
  }

  return (
    <div className="show-list-container">
      {shows.map((show) => {
        const isAvailable = show.status === 'ON_SALE';
        const buttonText = isAvailable
          ? '去选座'
          : show.status === 'SOLD_OUT'
            ? '已满座'
            : '已停售';

        return (
          <div key={show.showId} className="show-list-card">
            <div className="show-list-time-info">
              <div className="show-list-start-time">{show.startTimeText}</div>
              <div className="show-list-end-time">{show.endTimeText} 散场</div>
            </div>
            <div className="show-list-details">
              <div className="show-list-version">{show.languageVersion}</div>
              <div className="show-list-auditorium">{show.auditoriumName}</div>
            </div>
            <div className="show-list-price-info">
              <div className="show-list-price">
                <span className="show-list-currency">¥</span>
                <span className="show-list-amount">{show.basePrice}</span>
              </div>
              {isAvailable && (
                <div className="show-list-seats">余 {show.availableSeatCount} 座</div>
              )}
            </div>
            <div className="show-list-actions">
              {isMobile ? (
                <MobileButton
                  color="primary"
                  size="small"
                  disabled={!isAvailable}
                  onClick={() => onSelectShow?.(show.showId)}
                  className="show-list-buy-btn"
                >
                  {buttonText}
                </MobileButton>
              ) : (
                <Button
                  type="primary"
                  disabled={!isAvailable}
                  onClick={() => onSelectShow?.(show.showId)}
                  className="show-list-buy-btn"
                >
                  {buttonText}
                </Button>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};
