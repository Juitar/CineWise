export type TravelTaskStatus =
  'PENDING' | 'GENERATING' | 'READY' | 'NOTIFIED' | 'COMPLETED' | 'CANCELLED' | 'FAILED';

export interface TravelTask {
  taskId: string;
  status: TravelTaskStatus;
  triggerAt: string | null;
  version: number;
  order: {
    orderId: string;
    orderNo: string;
    showId: string;
    showStartTime: string;
  };
  movie: {
    movieId: string;
    title: string;
    posterUrl: string | null;
    source: string;
    dataAt: string | null;
  };
  cinema: {
    cinemaId: string;
    name: string;
    area: string | null;
    address: string | null;
    source: string;
    dataAt: string | null;
    expiresAt: string | null;
    isExpired: boolean;
  };
}

export interface TravelAdvice {
  available: boolean;
  taskId: string;
  taskStatus: TravelTaskStatus;
  weather: { area: string | null; condition: string | null; risk: string | null } | null;
  advice: Array<{ type: 'WEATHER' | 'TRANSPORT'; text: string }>;
  source: string | null;
  dataAt: string | null;
  expiresAt: string | null;
  isExpired: boolean;
  degraded: boolean;
  fallbackType: string | null;
}

export interface UpdateReminderRequest {
  triggerAt: string;
  version: number;
}

export interface TravelTaskUpdate {
  taskId: string;
  orderId: string;
  status: TravelTaskStatus;
  triggerAt: string | null;
  version: number;
}
