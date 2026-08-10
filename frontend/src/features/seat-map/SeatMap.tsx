import React, { useMemo } from 'react';
import type { SeatMapResponse, SeatItem } from '../../modules/ticketing/types';
import './index.css';

export interface SeatMapProps {
  seatMap: SeatMapResponse | null;
  selectedSeatIds: string[];
  onSeatToggle: (seatId: string) => void;
  maxSelectCount?: number;
  onLimitExceeded?: () => void;
}

/**
 * 自适应座位地图组件（支持 PC 宽屏及 H5 触控展示，移动端触控目标 >= 44px）
 * 强制约束最多选 6 个可用座位。
 */
export const SeatMap: React.FC<SeatMapProps> = ({
  seatMap,
  selectedSeatIds,
  onSeatToggle,
  maxSelectCount = 6,
  onLimitExceeded,
}) => {
  const rowGroups = useMemo(() => {
    if (!seatMap || !seatMap.seats) {
      return [];
    }
    const map = new Map<string, SeatItem[]>();
    seatMap.seats.forEach((seat) => {
      const list = map.get(seat.rowNo) ?? [];
      list.push(seat);
      map.set(seat.rowNo, list);
    });
    return Array.from(map.entries()).map(([rowNo, seats]) => ({
      rowNo,
      seats: seats.sort((a, b) => a.seatNo.localeCompare(b.seatNo, undefined, { numeric: true })),
    }));
  }, [seatMap]);

  if (!seatMap || rowGroups.length === 0) {
    return (
      <div className="seat-map-container">
        <div className="seat-map-empty">座位图数据加载中...</div>
      </div>
    );
  }

  const handleSeatClick = (seat: SeatItem) => {
    if (seat.status !== 'AVAILABLE') {
      return;
    }
    const isSelected = selectedSeatIds.includes(seat.seatId);
    if (!isSelected && selectedSeatIds.length >= maxSelectCount) {
      onLimitExceeded?.();
      return;
    }
    onSeatToggle(seat.seatId);
  };

  return (
    <div className="seat-map-container">
      <div className="seat-map-screen-wrapper">
        <div className="seat-map-screen">{seatMap.auditoriumName} 银幕方向</div>
      </div>

      <div className="seat-map-legend">
        <div className="legend-item">
          <span className="legend-box available" />
          <span>可选</span>
        </div>
        <div className="legend-item">
          <span className="legend-box selected" />
          <span>已选</span>
        </div>
        <div className="legend-item">
          <span className="legend-box locked" />
          <span>已锁</span>
        </div>
        <div className="legend-item">
          <span className="legend-box sold" />
          <span>已售</span>
        </div>
      </div>

      <div className="seat-map-grid" role="grid" aria-label="影厅选座网格">
        {rowGroups.map(({ rowNo, seats }) => (
          <div key={rowNo} className="seat-map-row" role="row">
            <div className="seat-map-row-label">{rowNo}</div>
            <div className="seat-map-row-seats">
              {seats.map((seat) => {
                const isSelected = selectedSeatIds.includes(seat.seatId);
                const isAvailable = seat.status === 'AVAILABLE';
                let statusClass = 'available';
                if (isSelected) {
                  statusClass = 'selected';
                } else if (seat.status === 'LOCKED') {
                  statusClass = 'locked';
                } else if (seat.status === 'SOLD') {
                  statusClass = 'sold';
                }

                return (
                  <button
                    key={seat.seatId}
                    type="button"
                    role="checkbox"
                    aria-checked={isSelected}
                    aria-label={`${seat.seatLabel} ${
                      isSelected ? '已先选' : seat.status === 'AVAILABLE' ? '可选' : '不可选'
                    }`}
                    disabled={!isAvailable}
                    className={`seat-button ${statusClass}`}
                    onClick={() => handleSeatClick(seat)}
                  >
                    {seat.seatNo}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
