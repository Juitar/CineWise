/**
 * 票务模块通用类型定义（场次与选座）
 * 注意：ID 和金额皆以 string 形式保存，严禁转为 Number。
 */

/** 场次基础摘要描述 */
export interface ShowSummary {
  showId: string;
  movieId: string;
  cinemaId: string;
  cinemaName: string;
  auditoriumId: string;
  auditoriumName: string;
  startTime: string;
  endTime: string;
  expiresAt: string;
  languageVersion: string;
  basePrice: string;
  availableSeatCount: number;
  status: 'ON_SALE' | 'OFF_SALE' | 'SOLD_OUT';
  dataType: string;
  stateVersion: number;
  updatedAt: string;
}

/** 列表中单个座位项 */
export interface SeatItem {
  seatId: string;
  rowNo: string;
  seatNo: string;
  seatLabel: string;
  status: 'AVAILABLE' | 'LOCKED' | 'SOLD';
  stateVersion: number;
}

/** 影厅选座地图响应结构 */
export interface SeatMapResponse {
  showId: string;
  auditoriumId: string;
  auditoriumName: string;
  rowCount: number;
  seatCount: number;
  availableSeatCount: number;
  stateVersion: number;
  updatedAt: string;
  seats: SeatItem[];
}
