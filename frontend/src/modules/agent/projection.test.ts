import { describe, expect, it } from 'vitest';

import duplicateEvent from '../../../../backend/src/test/resources/fixtures/agent/c/duplicate-event.json';
import errorEvent from '../../../../backend/src/test/resources/fixtures/agent/c/error-event.json';
import errorCard from '../../../../backend/src/test/resources/fixtures/agent/c/error-card.json';
import locationPermissionQuestion from '../../../../backend/src/test/resources/fixtures/agent/c/location-permission-question.json';
import orderConfirmCard from '../../../../backend/src/test/resources/fixtures/agent/c/order-confirm-card.json';
import travelAdviceCard from '../../../../backend/src/test/resources/fixtures/agent/c/travel-advice-card.json';
import movieCard from '../../../../backend/src/test/resources/fixtures/agent/c/recommendation-card.json';
import planCard from '../../../../backend/src/test/resources/fixtures/agent/c/plan-card.json';
import processingEvent from '../../../../backend/src/test/resources/fixtures/agent/c/processing-event.json';
import progressCard from '../../../../backend/src/test/resources/fixtures/agent/c/progress-card.json';
import questionCard from '../../../../backend/src/test/resources/fixtures/agent/c/question-card.json';
import textCard from '../../../../backend/src/test/resources/fixtures/agent/c/text-card.json';
import unknownEventType from '../../../../backend/src/test/resources/fixtures/agent/c/unknown-event-type.json';
import unknownPayloadType from '../../../../backend/src/test/resources/fixtures/agent/c/unknown-payload-type.json';
import { parseAgentEvent } from './contract';
import {
  buildProjectionFromHistoryAndSnapshots,
  compareDecimalStrings,
  consumeAgentEvent,
  createAgentProjection,
  safeConfirmationStatusText,
} from './projection';

describe('Agent 事件投影', () => {
  it('按任意长度十进制字符串比较游标', () => {
    expect(compareDecimalStrings('9007199254740993', '9007199254740992')).toBe(1);
    expect(compareDecimalStrings('00043', '43')).toBe(0);
    expect(compareDecimalStrings('9', '10')).toBe(-1);
  });

  it('忽略其他会话、其他运行和重复事件且不推进游标', () => {
    const initial = createAgentProjection('session-example-1');
    const first = consumeAgentEvent(initial, parseAgentEvent(processingEvent));
    expect(first.outcome).toBe('applied');
    if (first.outcome !== 'applied') throw new Error();
    const otherSession = consumeAgentEvent(
      first.projection,
      parseAgentEvent({ ...duplicateEvent, sessionId: 'session-example-2' }),
    );
    const otherRun = consumeAgentEvent(
      first.projection,
      parseAgentEvent({ ...duplicateEvent, runId: 'run-example-2' }),
    );
    const duplicate = consumeAgentEvent(first.projection, parseAgentEvent(processingEvent));
    expect(otherSession.outcome).toBe('ignored');
    expect(otherRun.outcome).toBe('ignored');
    expect(duplicate.outcome).toBe('ignored');
    expect(otherSession.projection.lastEventId).toBe('40');
  });

  it('旧 planVersion 事件不能覆盖新计划', () => {
    const current = consumeAgentEvent(
      createAgentProjection('session-example-1'),
      parseAgentEvent({ ...processingEvent, planVersion: 2 }),
    );
    if (current.outcome !== 'applied') throw new Error();
    const stale = consumeAgentEvent(
      current.projection,
      parseAgentEvent({ ...duplicateEvent, eventId: '44', planVersion: 1 }),
    );
    expect(stale.outcome).toBe('ignored');
    expect(stale.projection.planVersion).toBe(2);
    expect(stale.projection.lastEventId).toBe('40');
  });

  it('正常 MOVIE_CARD 只展示实际字段，后续失败不清空成功内容', () => {
    const card = consumeAgentEvent(
      createAgentProjection('session-example-1'),
      parseAgentEvent(movieCard),
    );
    if (card.outcome !== 'applied') throw new Error();
    const failed = consumeAgentEvent(
      card.projection,
      parseAgentEvent({ ...errorEvent, eventId: '43' }),
    );
    if (failed.outcome !== 'applied') throw new Error();
    expect(failed.projection.status).toBe('FAILED');
    expect(failed.projection.items).toEqual([
      expect.objectContaining({
        kind: 'movie-card',
        title: '暂未找到可购场次',
        text: expect.stringContaining('降级数据'),
      }),
      expect.objectContaining({ kind: 'error', text: '本次请求未完成' }),
    ]);
    expect(JSON.stringify(failed.projection)).not.toContain('RUN_FAILED');
  });

  it('正常 PLAN_CARD 投影正式方案字段且不展示业务 ID', () => {
    const result = consumeAgentEvent(
      createAgentProjection('session-example-1'),
      parseAgentEvent(planCard),
    );
    expect(result.outcome).toBe('applied');
    expect(result.projection.items[0]).toEqual(
      expect.objectContaining({ kind: 'plan-card', title: '推荐场次' }),
    );
    expect(result.projection.items[0].plans).toEqual([
      expect.objectContaining({
        showId: '3001',
        movieName: '示例影片',
        cinemaName: '示例影院',
        price: '68.00',
        reasons: ['匹配条件'],
      }),
    ]);
    expect(result.projection.items[0].fields).not.toContainEqual(
      expect.objectContaining({ label: expect.stringMatching(/ID/) }),
    );
  });

  it('TEXT 使用纯文本投影，过期空方案卡仍明确显示过期且不生成业务按钮', () => {
    const text = consumeAgentEvent(
      createAgentProjection('session-example-1'),
      parseAgentEvent({
        ...textCard,
        payload: { ...textCard.payload, text: '<strong>请告诉我想看的影片</strong>' },
      }),
    );
    expect(text.projection.items[0]).toEqual(
      expect.objectContaining({
        kind: 'assistant-text',
        text: '<strong>请告诉我想看的影片</strong>',
      }),
    );
    const expired = consumeAgentEvent(
      text.projection,
      parseAgentEvent({
        ...planCard,
        eventId: '9007199254740993',
        payload: {
          ...planCard.payload,
          title: '已过期',
          plans: [],
          expiresAt: '2026-08-05T09:05:00+08:00',
        },
      }),
    );
    expect(expired.projection.lastEventId).toBe('9007199254740993');
    expect(expired.projection.items[1].fields).toContainEqual({ label: '状态', value: '已过期' });
    expect(JSON.stringify(expired.projection)).not.toMatch(/确认|支付|购票按钮/);
  });

  it('QUESTION、PROGRESS 和 ERROR 使用类型化安全投影', () => {
    const question = consumeAgentEvent(
      createAgentProjection('session-example-1'),
      parseAgentEvent(questionCard),
    );
    expect(question.projection.items[0]).toEqual(
      expect.objectContaining({
        kind: 'question',
        text: '想在哪天观看？',
        question: expect.objectContaining({
          options: [{ optionId: 'today', label: '今天', value: '2026-08-05' }],
        }),
      }),
    );
    const progress = consumeAgentEvent(question.projection, parseAgentEvent(progressCard));
    const error = consumeAgentEvent(progress.projection, parseAgentEvent(errorCard));
    expect(error.projection.items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ kind: 'progress', text: progressCard.displayText }),
        expect.objectContaining({ kind: 'error', text: '暂时无法完成' }),
      ]),
    );
    expect(JSON.stringify(error.projection)).not.toContain('内部详情');
  });

  it('推荐卡只投影已校验的候选标题，不带未声明字段', () => {
    const result = consumeAgentEvent(
      createAgentProjection('session-example-1'),
      parseAgentEvent({
        ...movieCard,
        payload: {
          ...movieCard.payload,
          movies: [
            {
              ...movieCard.payload.movies[0],
              internalToolArgs: '不应展示',
            },
          ],
        },
      }),
    );
    expect(result.projection.items[0].fields).toContainEqual({
      label: '影片 1 · 片名',
      value: '示例影片',
    });
    expect(JSON.stringify(result.projection.items[0])).not.toContain('internalToolArgs');
    expect(JSON.stringify(result.projection.items[0])).not.toContain('不应展示');
  });

  it.each([
    ['NOT_REQUESTED', '等待位置授权'],
    ['GRANTED', '已授权当前位置'],
    ['DENIED', '可手动输入地点'],
    ['EXPIRED', '位置授权已过期'],
  ])('LOCATION_PERMISSION 状态 %s 只展示固定安全说明', (authorizationState, expected) => {
    const result = consumeAgentEvent(
      createAgentProjection('session-example-1'),
      parseAgentEvent({
        ...locationPermissionQuestion,
        payload: {
          ...locationPermissionQuestion.payload,
          locationAuthorization: {
            ...locationPermissionQuestion.payload.locationAuthorization,
            authorizationState,
          },
        },
      }),
    );
    expect(result.projection.items[0].text).toContain(expected);
    expect(JSON.stringify(result.projection)).not.toMatch(/经度|纬度|坐标/);
  });

  it('已知卡片缺字段时不更新内容、不推进游标并保留成功结果', () => {
    const current = consumeAgentEvent(
      createAgentProjection('session-example-1'),
      parseAgentEvent(movieCard),
    );
    const rejected = consumeAgentEvent(
      current.projection,
      parseAgentEvent({
        ...planCard,
        eventId: '52',
        payload: { ...planCard.payload, title: undefined },
      }),
    );
    expect(rejected.outcome).toBe('rejected');
    expect(rejected.projection.lastEventId).toBe('42');
    expect(rejected.projection.items).toEqual(current.projection.items);
    expect(rejected.projection.safeError).toBe('收到的 Agent 内容不完整，已保留当前结果');
  });

  it.each([unknownEventType, unknownPayloadType])(
    '未知事件或 payload 类型安全降级并推进游标',
    (value) => {
      const result = consumeAgentEvent(
        createAgentProjection('session-example-1'),
        parseAgentEvent(value),
      );
      if (value.eventType === 'card.future') {
        expect(result.outcome).toBe('ignored');
        expect(result.projection.lastEventId).not.toBe(value.eventId);
      } else {
        expect(result.outcome).toBe('applied');
        expect(result.projection.lastEventId).toBe(value.eventId);
        expect(result.projection.items[0]).toEqual(
          expect.objectContaining({
            kind: 'card-placeholder',
            text: expect.stringContaining('安全隐藏'),
          }),
        );
        expect(JSON.stringify(result.projection)).not.toMatch(/unsafe|<b>/);
      }
    },
  );

  it('确认卡展示脱敏摘要和可执行状态', () => {
    const result = consumeAgentEvent(
      createAgentProjection('session-1'),
      parseAgentEvent(orderConfirmCard),
    );
    expect(result.outcome).toBe('applied');
    expect(result.projection.items).toEqual([
      expect.objectContaining({
        kind: 'plan-card',
        title: '确认建单',
        text: '等待你的确认',
        confirmation: expect.objectContaining({
          status: 'PENDING_CONFIRMATION',
          submitting: false,
        }),
      }),
    ]);
    expect(JSON.stringify(result.projection.items[0].fields)).not.toContain('action-1');
  });

  it('确认 PLAN_CARD 不在可见字段中暴露 actionId', () => {
    const result = consumeAgentEvent(
      createAgentProjection('session-1'),
      parseAgentEvent({
        ...orderConfirmCard,
        planId: 'plan-1',
        payload: {
          ...orderConfirmCard.payload,
          type: 'PLAN_CARD',
          title: '确认建单',
          plans: [],
          source: 'agent_confirmation',
          dataAt: '2026-08-05T16:30:00+08:00',
          expiresAt: '2026-08-05T16:35:00+08:00',
          degraded: false,
        },
      }),
    );
    expect(result.projection.items).toEqual([
      expect.objectContaining({ kind: 'plan-card', text: '等待你的确认' }),
    ]);
    expect(JSON.stringify(result.projection.items[0].fields)).not.toContain('action-1');
  });

  it('只用同一运行和操作的最新确认事件替换对应历史卡片', () => {
    const history = [
      {
        messageId: 'message-confirm-1',
        runId: 'run-1',
        role: 'ASSISTANT',
        type: 'PLAN_CARD',
        text: '请确认建单',
        payload: { ...orderConfirmCard.payload, status: 'PENDING_CONFIRMATION' },
        status: 'COMPLETED',
        completedAt: '2026-08-05T16:30:00+08:00',
        createdAt: '2026-08-05T16:30:00+08:00',
      },
      {
        messageId: 'message-other-plan',
        runId: 'run-other',
        role: 'ASSISTANT',
        type: 'PLAN_CARD',
        text: '其他运行的卡片',
        payload: {},
        status: 'COMPLETED',
        completedAt: '2026-08-05T16:30:00+08:00',
        createdAt: '2026-08-05T16:30:00+08:00',
      },
      {
        messageId: 'message-confirm-2',
        runId: 'run-2',
        role: 'ASSISTANT',
        type: 'PLAN_CARD',
        text: '请确认另一项操作',
        payload: {
          ...orderConfirmCard.payload,
          actionId: 'action-2',
          status: 'PENDING_CONFIRMATION',
        },
        status: 'COMPLETED',
        completedAt: '2026-08-05T16:30:00+08:00',
        createdAt: '2026-08-05T16:30:00+08:00',
      },
    ];
    const pending = parseAgentEvent({ ...orderConfirmCard, eventId: '90001', runId: 'run-1' });
    const succeeded = parseAgentEvent({
      ...orderConfirmCard,
      eventId: '90002',
      runId: 'run-1',
      payload: { ...orderConfirmCard.payload, status: 'SUCCEEDED' },
    });
    const rejected = parseAgentEvent({
      ...orderConfirmCard,
      eventId: '90003',
      runId: 'run-2',
      payload: { ...orderConfirmCard.payload, actionId: 'action-2', status: 'REJECTED' },
    });
    const projection = buildProjectionFromHistoryAndSnapshots('session-1', history, [
      {
        runId: 'run-1',
        sessionId: 'session-1',
        status: 'COMPLETED',
        planId: 'plan-1',
        planVersion: 2,
        startedAt: '2026-08-05T16:30:00+08:00',
        finishedAt: '2026-08-05T16:31:00+08:00',
        lastEventId: '90002',
        messages: [],
        steps: [],
        events: [pending, succeeded],
      },
      {
        runId: 'run-2',
        sessionId: 'session-1',
        status: 'COMPLETED',
        planId: 'plan-1',
        planVersion: 2,
        startedAt: '2026-08-05T16:30:00+08:00',
        finishedAt: '2026-08-05T16:31:00+08:00',
        lastEventId: '90003',
        messages: [],
        steps: [],
        events: [rejected],
      },
    ]);

    expect(projection.items).toHaveLength(3);
    expect(projection.items[0].confirmation?.status).toBe('SUCCEEDED');
    expect(projection.items[1]).toMatchObject({
      kind: 'card-placeholder',
      text: '卡片数据暂不完整',
    });
    expect(projection.items[2].confirmation?.status).toBe('REJECTED');
  });

  it('初次加载会从运行快照恢复历史 TEXT 中的出行建议卡片', () => {
    const history = [
      {
        messageId: 'message-travel-advice',
        runId: 'run-travel',
        role: 'ASSISTANT',
        type: 'TEXT',
        text: '已查询到出行建议',
        payload: travelAdviceCard.payload,
        status: 'COMPLETED',
        completedAt: '2026-08-07T10:00:00Z',
        createdAt: '2026-08-07T10:00:00Z',
      },
    ];
    const event = parseAgentEvent({ ...travelAdviceCard, runId: 'run-travel' });
    const projection = buildProjectionFromHistoryAndSnapshots('session-example-1', history, [
      {
        runId: 'run-travel',
        sessionId: 'session-example-1',
        status: 'COMPLETED',
        planId: 'plan-example-2',
        planVersion: 1,
        startedAt: null,
        finishedAt: null,
        lastEventId: event.eventId,
        messages: [],
        steps: [],
        events: [event],
      },
    ]);
    expect(projection.items).toHaveLength(1);
    expect(projection.items[0]).toMatchObject({
      kind: 'travel-advice-card',
      travelTaskId: '90001',
    });
  });

  it.each([
    ['EXECUTING', '确认操作处理中'],
    ['SUCCEEDED', '确认操作已完成'],
    ['FAILED', '确认操作未完成'],
    ['REJECTED', '确认操作已拒绝'],
    ['RESULT_UNKNOWN', '确认结果暂时无法确定，请等待状态恢复'],
    ['EXPIRED', '确认操作已过期'],
    ['INVALIDATED', '确认内容已失效'],
  ])('确认结果 %s 只映射固定状态文案', (status, expected) => {
    expect(safeConfirmationStatusText(status)).toBe(expected);
  });

  it.each([
    [
      'COMPLETED',
      {
        ...duplicateEvent,
        eventId: '50',
        eventType: 'run.complete',
        payload: { status: 'COMPLETED' },
      },
    ],
    [
      'FAILED',
      {
        ...duplicateEvent,
        eventId: '50',
        eventType: 'run.complete',
        payload: { status: 'FAILED' },
      },
    ],
    [
      'CANCELLED',
      {
        ...duplicateEvent,
        eventId: '50',
        eventType: 'run.complete',
        payload: { status: 'CANCELLED' },
      },
    ],
  ] as const)('运行终态投影为 %s', (status, value) => {
    const result = consumeAgentEvent(
      createAgentProjection('session-example-1'),
      parseAgentEvent(value),
    );
    expect(result.outcome).toBe('applied');
    expect(result.projection.status).toBe(status);
  });
});
