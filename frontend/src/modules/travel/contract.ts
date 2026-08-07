import { parseOrderDateTime } from '../order/formatters';
import type {
  TravelAdvice,
  TravelMode,
  TravelRoute,
  TravelTask,
  TravelTaskStatus,
  TravelTaskUpdate,
} from './types';

const BUSINESS_ID = /^[1-9][0-9]*$/;
const TASK_STATUSES = new Set<TravelTaskStatus>([
  'PENDING',
  'GENERATING',
  'READY',
  'NOTIFIED',
  'COMPLETED',
  'CANCELLED',
  'FAILED',
]);
const ADVICE_TYPES = new Set(['WEATHER', 'TRANSPORT']);
const TRAVEL_MODES = new Set<TravelMode>(['DRIVING', 'WALKING']);

export class TravelContractError extends Error {
  constructor() {
    super('出行服务返回的数据格式不正确');
    this.name = 'TravelContractError';
  }
}

function record(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new TravelContractError();
  }
  return value as Record<string, unknown>;
}

function text(value: unknown, allowEmpty = false): string {
  if (typeof value !== 'string' || (!allowEmpty && value.trim().length === 0)) {
    throw new TravelContractError();
  }
  return value;
}

function optionalText(value: unknown): string | null {
  return value === null ? null : text(value);
}

function businessId(value: unknown): string {
  const id = text(value);
  if (!BUSINESS_ID.test(id)) throw new TravelContractError();
  return id;
}

function status(value: unknown): TravelTaskStatus {
  const candidate = text(value) as TravelTaskStatus;
  if (!TASK_STATUSES.has(candidate)) throw new TravelContractError();
  return candidate;
}

function version(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw new TravelContractError();
  }
  return value;
}

function nonNegativeInteger(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw new TravelContractError();
  }
  return value;
}

function boolean(value: unknown): boolean {
  if (typeof value !== 'boolean') throw new TravelContractError();
  return value;
}

function dateTime(value: unknown, nullable = false): string | null {
  if (value === null && nullable) return null;
  const candidate = text(value);
  if (!parseOrderDateTime(candidate)) throw new TravelContractError();
  return candidate;
}

export function parseTravelTask(value: unknown): TravelTask {
  const task = record(value);
  const order = record(task.order);
  const movie = record(task.movie);
  const cinema = record(task.cinema);
  return {
    taskId: businessId(task.taskId),
    status: status(task.status),
    triggerAt: dateTime(task.triggerAt, true),
    version: version(task.version),
    order: {
      orderId: businessId(order.orderId),
      orderNo: text(order.orderNo),
      showId: businessId(order.showId),
      showStartTime: dateTime(order.showStartTime) as string,
    },
    movie: {
      movieId: businessId(movie.movieId),
      title: text(movie.title),
      posterUrl: optionalText(movie.posterUrl),
      source: text(movie.source),
      dataAt: dateTime(movie.dataAt, true),
    },
    cinema: {
      cinemaId: businessId(cinema.cinemaId),
      name: text(cinema.name),
      area: optionalText(cinema.area),
      address: optionalText(cinema.address),
      source: text(cinema.source),
      dataAt: dateTime(cinema.dataAt, true),
      expiresAt: dateTime(cinema.expiresAt, true),
      isExpired: boolean(cinema.isExpired),
    },
  };
}

export function parseTravelAdvice(value: unknown): TravelAdvice {
  const summary = record(value);
  const weatherValue = summary.weather;
  const weather =
    weatherValue === null
      ? null
      : (() => {
          const item = record(weatherValue);
          return {
            area: optionalText(item.area),
            condition: optionalText(item.condition),
            risk: optionalText(item.risk),
          };
        })();
  if (!Array.isArray(summary.advice)) throw new TravelContractError();
  const advice = summary.advice.map((value) => {
    const item = record(value);
    const type = text(item.type);
    if (!ADVICE_TYPES.has(type)) throw new TravelContractError();
    return { type: type as 'WEATHER' | 'TRANSPORT', text: text(item.text) };
  });
  return {
    available: boolean(summary.available),
    taskId: businessId(summary.taskId),
    taskStatus: status(summary.taskStatus),
    weather,
    advice,
    source: optionalText(summary.source),
    dataAt: dateTime(summary.dataAt, true),
    expiresAt: dateTime(summary.expiresAt, true),
    isExpired: boolean(summary.isExpired),
    degraded: boolean(summary.degraded),
    fallbackType: optionalText(summary.fallbackType),
  };
}

export function parseTravelTaskUpdate(value: unknown): TravelTaskUpdate {
  const update = record(value);
  return {
    taskId: businessId(update.taskId),
    orderId: businessId(update.orderId),
    status: status(update.status),
    triggerAt: dateTime(update.triggerAt, true),
    version: version(update.version),
  };
}

export function parseTravelRoute(value: unknown): TravelRoute {
  const route = record(value);
  const travelMode = text(route.travelMode) as TravelMode;
  if (!TRAVEL_MODES.has(travelMode)) throw new TravelContractError();
  return {
    provider: text(route.provider),
    travelMode,
    durationMinutes: nonNegativeInteger(route.durationMinutes),
    suggestedDepartureAt: dateTime(route.suggestedDepartureAt) as string,
    source: text(route.source),
    dataTime: dateTime(route.dataTime) as string,
    expiresAt: dateTime(route.expiresAt) as string,
    isExpired: boolean(route.isExpired),
    degraded: boolean(route.degraded),
    fallbackType: optionalText(route.fallbackType),
  };
}
