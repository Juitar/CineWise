import React from 'react';
import { Button } from 'antd';
import { Button as MobileButton } from 'antd-mobile';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

export interface SelectedSeatItem {
  id: string;
  label: string;
}

export interface SeatSelectionSummaryProps {
  /** 已选座位的集合 */
  selectedSeats: SelectedSeatItem[];
  /** 已选数量 */
  selectedCount: number;
  /** 最大可选数量 */
  maxSelectCount: number;
  /** 整体确认按钮是否应该禁用 */
  disabled?: boolean;
  /** 点击确认选座 */
  onConfirm: () => void;
}

/**
 * 选座摘要与底部确认操作纯展示组件。
 * 负责展示已选座位编号、数量上限提示，提供确认选座操作入口。
 */
export const SeatSelectionSummary: React.FC<SeatSelectionSummaryProps> = ({
  selectedSeats,
  selectedCount,
  maxSelectCount,
  disabled,
  onConfirm,
}) => {
  const isMobile = useMediaQuery('(max-width: 1023px)');
  const hasSelection = selectedCount > 0;
  const isMaxReached = selectedCount >= maxSelectCount;
  const buttonDisabled = disabled || !hasSelection;

  return (
    <div className="seat-summary-container">
      <div className="seat-summary-info">
        <div className="seat-summary-title">
          已选座位{' '}
          {selectedCount > 0 && (
            <span className="seat-summary-count">
              ({selectedCount}/{maxSelectCount})
            </span>
          )}
        </div>
        <div className="seat-summary-labels">
          {hasSelection ? (
            selectedSeats.map((seat) => (
              <span key={seat.id} className="seat-summary-label-tag">
                {seat.label}
              </span>
            ))
          ) : (
            <span className="seat-summary-placeholder">请在上方座位图选择座位</span>
          )}
        </div>
        {isMaxReached && (
          <div className="seat-summary-limit-warning">最多只能选择 {maxSelectCount} 个座位</div>
        )}
      </div>

      <div className="seat-summary-action">
        {isMobile ? (
          <MobileButton
            color="primary"
            disabled={buttonDisabled}
            onClick={onConfirm}
            className="seat-summary-confirm-btn"
          >
            确认选座
          </MobileButton>
        ) : (
          <Button
            type="primary"
            size="large"
            disabled={buttonDisabled}
            onClick={onConfirm}
            className="seat-summary-confirm-btn"
          >
            确认选座
          </Button>
        )}
      </div>
    </div>
  );
};
