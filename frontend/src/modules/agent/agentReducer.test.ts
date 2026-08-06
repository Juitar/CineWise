import { describe, expect, it } from 'vitest';

import planCard from './fixtures/plan-card.json';
import selectSeatsCard from './fixtures/select-seats-card.json';
import streamReset from './fixtures/stream-reset.json';
import toolComplete from './fixtures/tool-complete-degraded.json';
import toolError from './fixtures/tool-error-replan.json';
import toolStart from './fixtures/tool-start.json';
import { parseAgentEvent } from './contract';
import { consumeAgentEvent, createAgentProjection } from './projection';

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

  it('展示 SELECT_SEATS 入口但只使用完整外层计划字段和 businessRef.showId', () => {
    const result = consumeAgentEvent(
      createAgentProjection('session-1'),
      parseAgentEvent(selectSeatsCard),
    );
    expect(result.outcome).toBe('applied');
    expect(result.projection.items[0]).toEqual(
      expect.objectContaining({ kind: 'business-intent', text: '已确认场次，可选座' }),
    );
    expect(result.projection.items[0].fields).toEqual([{ label: '场次 ID', value: 'show-70001' }]);
  });

  it('SELECT_SEATS 缺少 showId 时拒绝且不推进游标', () => {
    const invalid = consumeAgentEvent(
      createAgentProjection('session-1'),
      parseAgentEvent({
        ...selectSeatsCard,
        payload: { type: 'BUSINESS_INTENT', payload: { intent: 'SELECT_SEATS', businessRef: {} } },
      }),
    );
    expect(invalid.outcome).toBe('rejected');
    expect(invalid.projection.lastEventId).toBe('0');
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
