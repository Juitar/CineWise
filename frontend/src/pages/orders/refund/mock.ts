import type { RefundVisualStatus } from '../../../features/refund-confirmation/RefundConfirmation';
import type {
  AlternativeShowItem,
} from '../../../features/alternative-show-list/AlternativeShowList';

export interface RefundPageMockData {
  orderNo: string;
  refundAmount: string;
  orderStatus: string;
  ticketStatus: string;
  refundStatus: string;
  showStartTime: string;
  impactText: string;
  stateVersion: number;
  alternativeShows: AlternativeShowItem[];
}

export const refundPageMockNormal: RefundPageMockData = {
  orderNo: '202608050001',
  refundAmount: '78.00',
  orderStatus: 'PAID',
  ticketStatus: 'VALID',
  refundStatus: 'SUCCESS',
  showStartTime: '2026-08-10T14:30:00+08:00',
  impactText: '距离开场时间大于 24 小时，退票需按相关规则核实。申请通过后座次将被释放。',
  stateVersion: 1,
  alternativeShows: [
    {
      showId: '1001',
      movieId: '2001',
      cinemaId: '3001',
      startTime: '2026-08-10T18:00:00+08:00',
      basePrice: '39.00',
      status: 'ON_SALE',
      availableSeatCount: 80,
    },
    {
      showId: '1002',
      movieId: '2001',
      cinemaId: '3001',
      startTime: '2026-08-10T21:00:00+08:00',
      basePrice: '45.00',
      status: 'ON_SALE',
      availableSeatCount: 52,
    },
  ],
};

export const refundPageMockUnknown = {
  ...refundPageMockNormal,
  status: 'RESULT_UNKNOWN' as RefundVisualStatus,
};

export const refundPageMockSuccess = {
  ...refundPageMockNormal,
  status: 'SUCCESS' as RefundVisualStatus,
};
