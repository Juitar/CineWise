import React from 'react';
import { Alert, Button } from 'antd';
import { Button as MobileButton } from 'antd-mobile';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

export interface OrderContentNoticeProps {
  isLoading: boolean;
  hasUnavailableContent: boolean;
  onRetry: () => void;
}

/** 订单内容资料的只读加载与降级提示；不会影响任何交易状态或操作。 */
export const OrderContentNotice: React.FC<OrderContentNoticeProps> = ({
  isLoading,
  hasUnavailableContent,
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

  if (!hasUnavailableContent) {
    return null;
  }

  return (
    <Alert
      className="order-content-notice"
      type="warning"
      showIcon
      message="部分影片或影院信息暂不可用"
      description={
        <div className="order-content-notice-description">
          <span>订单、金额、场次和座位信息不受影响。</span>
          {isMobile ? (
            <MobileButton size="small" fill="none" color="primary" onClick={onRetry}>
              重试内容信息
            </MobileButton>
          ) : (
            <Button type="link" size="small" onClick={onRetry}>
              重试内容信息
            </Button>
          )}
        </div>
      }
    />
  );
};

export default OrderContentNotice;
