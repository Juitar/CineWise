import React from 'react';
import { Alert, Button, Tag } from 'antd';
import { Button as MobileButton } from 'antd-mobile';
import { getFreshnessNotices } from '../../modules/content/freshness';
import type { ContentFreshness } from '../../shared/types/api';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

export interface OrderContentNoticeProps {
  isLoading: boolean;
  hasUnavailableContent: boolean;
  movieFreshness?: Array<Partial<ContentFreshness>>;
  cinemaFreshness?: Array<Partial<ContentFreshness>>;
  onRetry: () => void;
}

/** 订单内容资料的只读加载与降级提示；不会影响任何交易状态或操作。 */
export const OrderContentNotice: React.FC<OrderContentNoticeProps> = ({
  isLoading,
  hasUnavailableContent,
  movieFreshness = [],
  cinemaFreshness = [],
  onRetry,
}) => {
  const isMobile = useMediaQuery('(max-width: 1023px)');

  if (isLoading) {
    return (
      <Alert
        className="order-content-notice"
        type="info"
        showIcon
        message="正在获取影片和影院信息"
      />
    );
  }

  const hasFreshness = movieFreshness.length > 0 || cinemaFreshness.length > 0;
  if (!hasUnavailableContent && !hasFreshness) {
    return null;
  }

  const freshnessGroups = [
    { label: '影片资料', entries: movieFreshness },
    { label: '影院资料', entries: cinemaFreshness },
  ].flatMap(({ label, entries }) =>
    entries.flatMap((entry) => getFreshnessNotices(entry).map((notice) => ({ label, notice }))),
  );

  return (
    <Alert
      className="order-content-notice"
      type="warning"
      showIcon
      message={hasUnavailableContent ? '部分影片或影院信息暂不可用' : '内容资料来源与时效'}
      description={
        <div className="order-content-notice-description">
          {hasUnavailableContent && <span>订单、金额、场次和座位信息不受影响。</span>}
          {freshnessGroups.length > 0 && (
            <div className="order-content-freshness" aria-label="内容资料来源和时效">
              {freshnessGroups.map(({ label, notice }) => (
                <Tag
                  color={notice.tone === 'warning' ? 'orange' : 'blue'}
                  key={`${label}-${notice.id}-${notice.text}`}
                >
                  {label}：{notice.text}
                </Tag>
              ))}
            </div>
          )}
          {hasUnavailableContent &&
            (isMobile ? (
              <MobileButton size="small" fill="none" color="primary" onClick={onRetry}>
                重试内容信息
              </MobileButton>
            ) : (
              <Button type="link" size="small" onClick={onRetry}>
                重试内容信息
              </Button>
            ))}
        </div>
      }
    />
  );
};

export default OrderContentNotice;
