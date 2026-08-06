import React from 'react';
import { Button } from 'antd';
import { Button as MobileButton } from 'antd-mobile';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

export interface TransactionBackButtonProps {
  onBack?: () => void;
  label?: string;
}

/**
 * 交易页面的安全业务返回入口。
 *
 * <p>页面容器传入业务目标路径，避免浏览器历史回到已经使用过的建单座位地址。</p>
 */
export const TransactionBackButton: React.FC<TransactionBackButtonProps> = ({
  onBack,
  label = '返回上一步',
}) => {
  const isMobile = useMediaQuery('(max-width: 1023px)');
  const ariaLabel = `${label}（不重新提交订单）`;

  return (
    <div className="transaction-back-button-wrapper">
      {isMobile ? (
        <MobileButton
          className="transaction-back-button"
          fill="none"
          onClick={onBack}
          aria-label={ariaLabel}
        >
          ‹
        </MobileButton>
      ) : (
        <Button
          type="text"
          className="transaction-back-button"
          onClick={onBack}
          aria-label={ariaLabel}
        >
          ‹
        </Button>
      )}
      <span className="transaction-back-button-label">{label}</span>
    </div>
  );
};

export default TransactionBackButton;
