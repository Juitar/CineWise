import { describe, expect, it } from 'vitest';

import planCard from './fixtures/plan-card.json';
import selectSeatsCard from './fixtures/select-seats-card.json';
import streamReset from './fixtures/stream-reset.json';
import toolComplete from './fixtures/tool-complete-degraded.json';
import toolError from './fixtures/tool-error-replan.json';
import toolStart from './fixtures/tool-start.json';
import { parseAgentEvent } from './contract';
import { buildAgentSelectSeatsPath, consumeAgentEvent, createAgentProjection } from './projection';

describe('Agent reducer 新 SSE 协议', () => {
  it('按 tool.start -> tool.error -> plan.replanned -> tool.complete 处理，tool.error 不结束运行', () => {
    let projection = createAgentProjection('session-1');
    for (const fixture of [toolStart, toolError]) {
      const result = consumeAgentEvent(projection, parseAgentEvent(fixture));
      expect(result.outcome).toBe('applied');
      projection = result.projection;
    }
    expect(projection.status).toBe('STREAMING');
    const replanned = consumeAgentEvent(
      projection,
      parseAgentEvent({
        ...toolStart,
        eventId: '43',
        eventType: 'plan.replanned',
        planVersion: 3,
        payload: { planVersion: 3 },
      }),
    );
    expect(replanned.outcome).toBe('applied');
    const complete = consumeAgentEvent(
      replanned.projection,
      parseAgentEvent({ ...toolComplete, eventId: '44', planVersion: 3 }),
    );
    expect(complete.outcome).toBe('applied');
    expect(complete.projection.status).toBe('STREAMING');
    expect(complete.projection.lastEventId).toBe('44');
  });

  it('接受后端数字工具错误码并推进合法事件游标', () => {
    const result = consumeAgentEvent(
      createAgentProjection('session-1'),
      parseAgentEvent(toolError),
    );
    expect(result.outcome).toBe('applied');
    expect(result.projection.lastEventId).toBe('42');
  });

  it('展示 SELECT_SEATS 入口并使用已校验的 businessRef 三个 ID', () => {
    const result = consumeAgentEvent(
      createAgentProjection('session-1'),
      parseAgentEvent(selectSeatsCard),
    );
    expect(result.outcome).toBe('applied');
    expect(result.projection.items[0]).toEqual(
      expect.objectContaining({ kind: 'business-intent', text: '已确认场次，可选座' }),
    );
    expect(result.projection.items[0].fields).toEqual([
      { label: '场次 ID', value: '9223372036854775807' },
      { label: '影片 ID', value: '9007199254740993' },
      { label: '影院 ID', value: '8000000000000001' },
    ]);
    expect(result.projection.items[0].selectSeatsPath).toBe(
      '/shows/9223372036854775807/seats?movieId=9007199254740993&cinemaId=8000000000000001',
    );
  });

  it('只使用已校验的十进制 ID，不补齐其他交易参数', () => {
    expect(
      buildAgentSelectSeatsPath('9223372036854775807', '9007199254740993', '8000000000000001'),
    ).toBe('/shows/9223372036854775807/seats?movieId=9007199254740993&cinemaId=8000000000000001');
  });

  it.each(['showId', 'movieId', 'cinemaId'])(
    'SELECT_SEATS 缺少 %s 时拒绝且不推进游标',
    (missingKey) => {
      const businessRef = {
        showId: '9223372036854775807',
        movieId: '9007199254740993',
        cinemaId: '8000000000000001',
      };
      delete businessRef[missingKey as keyof typeof businessRef];
      const invalid = consumeAgentEvent(
        createAgentProjection('session-1'),
        parseAgentEvent({
          ...selectSeatsCard,
          payload: { type: 'BUSINESS_INTENT', payload: { intent: 'SELECT_SEATS', businessRef } },
        }),
      );
      expect(invalid.outcome).toBe('rejected');
      expect(invalid.projection.lastEventId).toBe('0');
    },
  );

  it.each(['showId', 'movieId', 'cinemaId'])(
    'SELECT_SEATS 使用非法 %s 时拒绝、不显示入口且不推进游标',
    (invalidKey) => {
      const businessRef = {
        showId: '9223372036854775807',
        movieId: '9007199254740993',
        cinemaId: '8000000000000001',
      };
      businessRef[invalidKey as keyof typeof businessRef] = 'show-70001';
      const invalid = consumeAgentEvent(
        createAgentProjection('session-1'),
        parseAgentEvent({
          ...selectSeatsCard,
          payload: { type: 'BUSINESS_INTENT', payload: { intent: 'SELECT_SEATS', businessRef } },
        }),
      );
      expect(invalid.outcome).toBe('rejected');
      expect(invalid.projection.items).toHaveLength(0);
      expect(invalid.projection.lastEventId).toBe('0');
    },
  );

  it.each(['showId', 'movieId', 'cinemaId'])(
    'SELECT_SEATS 的 %s 超过 Java long 上限时拒绝且不推进游标',
    (invalidKey) => {
      const businessRef = {
        showId: '9223372036854775807',
        movieId: '9007199254740993',
        cinemaId: '8000000000000001',
      };
      businessRef[invalidKey as keyof typeof businessRef] = '9223372036854775808';
      const invalid = consumeAgentEvent(
        createAgentProjection('session-1'),
        parseAgentEvent({
          ...selectSeatsCard,
          payload: {
            type: 'BUSINESS_INTENT',
            payload: {
              intent: 'SELECT_SEATS',
              businessRef,
            },
          },
        }),
      );
      expect(invalid.outcome).toBe('rejected');
      expect(invalid.projection.lastEventId).toBe('0');
    },
  );

  it.each(['0', '-1', '001', 'not-a-number'])(
    'SELECT_SEATS 非正 Java long 十进制字符串 %s 时拒绝且不推进游标',
    (invalidId) => {
      const invalid = consumeAgentEvent(
        createAgentProjection('session-1'),
        parseAgentEvent({
          ...selectSeatsCard,
          payload: {
            type: 'BUSINESS_INTENT',
            payload: {
              intent: 'SELECT_SEATS',
              businessRef: {
                showId: invalidId,
                movieId: '9007199254740993',
                cinemaId: '8000000000000001',
              },
            },
          },
        }),
      );
      expect(invalid.outcome).toBe('rejected');
      expect(invalid.projection.lastEventId).toBe('0');
    },
  );

  it.each(['showId', 'movieId', 'cinemaId'])(
    'SELECT_SEATS 的 %s 不是 JSON string 时拒绝且不推进游标',
    (invalidKey) => {
      const businessRef: Record<string, unknown> = {
        showId: '9223372036854775807',
        movieId: '9007199254740993',
        cinemaId: '8000000000000001',
      };
      businessRef[invalidKey] = 9223372036854775807;
      const invalid = consumeAgentEvent(
        createAgentProjection('session-1'),
        parseAgentEvent({
          ...selectSeatsCard,
          payload: {
            type: 'BUSINESS_INTENT',
            payload: {
              intent: 'SELECT_SEATS',
              businessRef,
            },
          },
        }),
      );
      expect(invalid.outcome).toBe('rejected');
      expect(invalid.projection.lastEventId).toBe('0');
    },
  );

  it('真正完成消息与合法 run.complete 分别进入完成状态和对应终态', () => {
    const messageComplete = consumeAgentEvent(
      createAgentProjection('session-1'),
      parseAgentEvent({
        ...toolStart,
        eventType: 'message.complete',
        payload: { messageType: 'TEXT' },
      }),
    );
    expect(messageComplete.projection.status).toBe('COMPLETED');

    const cancelled = consumeAgentEvent(
      createAgentProjection('session-1'),
      parseAgentEvent({
        ...toolStart,
        eventId: '41',
        eventType: 'run.complete',
        payload: { status: 'CANCELLED' },
      }),
    );
    expect(cancelled.projection.status).toBe('CANCELLED');
  });

  it('重复事件和旧计划迟到事件不推进游标', () => {
    const first = consumeAgentEvent(createAgentProjection('session-1'), parseAgentEvent(planCard));
    expect(first.outcome).toBe('applied');
    const duplicate = consumeAgentEvent(first.projection, parseAgentEvent(planCard));
    expect(duplicate.outcome).toBe('ignored');
    const stale = consumeAgentEvent(
      first.projection,
      parseAgentEvent({ ...selectSeatsCard, eventId: '45', planVersion: 1 }),
    );
    expect(stale.outcome).toBe('ignored');
    expect(stale.projection.lastEventId).toBe('44');
  });

  it('未知事件忽略，非法工具 payload 不推进游标', () => {
    const initial = createAgentProjection('session-1');
    const unknown = consumeAgentEvent(
      initial,
      parseAgentEvent({ ...toolStart, eventId: '45', eventType: 'future.event' }),
    );
    expect(unknown.outcome).toBe('ignored');
    const invalid = consumeAgentEvent(
      initial,
      parseAgentEvent({ ...toolStart, eventId: '46', payload: { toolName: 'queryShows' } }),
    );
    expect(invalid.outcome).toBe('rejected');
    expect(invalid.projection.lastEventId).toBe('0');
  });

  it('stream.reset 使用事件水位线并交给恢复层，水位线不一致由恢复层拒绝', () => {
    const result = consumeAgentEvent(
      createAgentProjection('session-1'),
      parseAgentEvent(streamReset),
    );
    expect(result.outcome).toBe('reset-required');
    if (result.outcome === 'reset-required') {
      expect(result.event.eventId).toBe(result.event.payload.watermark);
      expect(result.projection.lastEventId).toBe('0');
    }
  });
});
