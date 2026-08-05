import React, { useMemo, useState } from 'react';
import { history, useParams, useSearchParams } from 'umi';
import { Button, Spin, Alert, message } from 'antd';
import { Button as MobileButton, ErrorBlock, SpinLoading } from 'antd-mobile';
import { useSeatMap } from '../../modules/ticketing/hooks';
import { SeatMap } from '../../features/seat-map/SeatMap';
import { SeatSelectionSummary } from '../../features/seat-selection-summary/SeatSelectionSummary';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

/**
 * 座位选择路由页面：/shows/:showId/seats?movieId={movieId}&cinemaId={cinemaId}
 * 只查询最新可售座位图，用户选中后拼接重复的 seatId 参数向 /orders/confirm 页面推进。
 */
export default function SeatsPage() {
  const params = useParams<{ showId: string }>();
  const showId = params.showId;
  const [searchParams] = useSearchParams();
  const movieId = searchParams.get('movieId') || '';
  const cinemaId = searchParams.get('cinemaId') || '';
  const isMobile = useMediaQuery('(max-width: 1023px)');

  const { loading, seatMap, error, refetch } = useSeatMap(showId);
  const [selectedSeatIds, setSelectedSeatIds] = useState<string[]>([]);

  const handleSeatToggle = (seatId: string) => {
    setSelectedSeatIds((prev) =>
      prev.includes(seatId) ? prev.filter((id) => id !== seatId) : [...prev, seatId],
    );
  };

  const handleLimitExceeded = () => {
    message.warning('每个订单最多支持选择 6 个座位');
  };

  const handleConfirmSeats = () => {
    if (!showId || selectedSeatIds.length === 0) {
      return;
    }
    const query = new URLSearchParams();
    query.append('showId', showId);
    if (movieId) {
      query.append('movieId', movieId);
    }
    if (cinemaId) {
      query.append('cinemaId', cinemaId);
    }
    // 严格遵照规范：重复添加 seatId，避免用英文逗号拼接
    selectedSeatIds.forEach((id) => {
      query.append('seatId', id);
    });
    history.push(`/orders/confirm?${query.toString()}`);
  };

  const selectedSeats = useMemo(
    () =>
      seatMap?.seats
        .filter((seat) => selectedSeatIds.includes(seat.seatId))
        .map((seat) => ({ id: seat.seatId, label: seat.seatLabel })) ?? [],
    [seatMap, selectedSeatIds],
  );

  if (!showId) {
    return (
      <div className="seats-page-container">
        {isMobile ? (
          <ErrorBlock status="default" description="缺少场次 ID 参数" />
        ) : (
          <Alert type="error" showIcon message="缺少场次 ID 参数" />
        )}
      </div>
    );
  }

  return (
    <div className="seats-page-container">
      <div className="seats-page-header">
        <div>
          <h1 className="seats-title">{seatMap ? seatMap.auditoriumName : '选择座位'}</h1>
          <span className="seats-sub-info">
            {seatMap ? `共有可用座位 ${seatMap.availableSeatCount} 个` : ''}
          </span>
        </div>
        {isMobile ? (
          <MobileButton onClick={() => history.back()} className="seats-back-button">
            返回场次
          </MobileButton>
        ) : (
          <Button onClick={() => history.back()}>返回场次</Button>
        )}
      </div>

      {error &&
        (isMobile ? (
          <div className="seats-error-mobile">
            <ErrorBlock
              status="default"
              title="加载座位图失败"
              description={error.message || '请检查登录状态或网络配置后重试'}
            />
            <MobileButton color="primary" onClick={refetch} className="seats-retry-button">
              重新加载
            </MobileButton>
          </div>
        ) : (
          <Alert
            type="error"
            showIcon
            message="加载座位图发生异常"
            description={error.message || '请检查登录状态或网络配置后重试'}
            action={
              <Button size="small" onClick={refetch}>
                重试
              </Button>
            }
            className="seats-error-alert"
          />
        ))}

      <div className="seats-main-area">
        {loading ? (
          <div className="seats-loading">
            {isMobile ? <SpinLoading color="primary" /> : <Spin tip="拉取最新可用座位..." />}
          </div>
        ) : (
          <SeatMap
            seatMap={seatMap}
            selectedSeatIds={selectedSeatIds}
            onSeatToggle={handleSeatToggle}
            maxSelectCount={6}
            onLimitExceeded={handleLimitExceeded}
          />
        )}
      </div>

      <SeatSelectionSummary
        selectedSeats={selectedSeats}
        selectedCount={selectedSeatIds.length}
        maxSelectCount={6}
        disabled={loading || selectedSeats.length !== selectedSeatIds.length}
        onConfirm={handleConfirmSeats}
      />
    </div>
  );
}
