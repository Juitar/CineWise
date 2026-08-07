import { describe, expect, it } from 'vitest';

import duplicateEvent from '../../../../backend/src/test/resources/fixtures/agent/c/duplicate-event.json';
import invalidLocationAuthorization from '../../../../backend/src/test/resources/fixtures/agent/c/invalid-location-authorization.json';
import messageHistory from '../../../../backend/src/test/resources/fixtures/agent/c/session-message-history.json';
import runCompleted from '../../../../backend/src/test/resources/fixtures/agent/c/run-completed.json';
import sessionCreated from '../../../../backend/src/test/resources/fixtures/agent/c/session-created.json';
import sessionList from '../../../../backend/src/test/resources/fixtures/agent/c/session-list.json';
import confirmationFixtures from '../../../../backend/src/test/resources/fixtures/agent/c/confirmation-api-fixtures.json';
import orderConfirmCard from '../../../../backend/src/test/resources/fixtures/agent/c/order-confirm-card.json';
import planCard from '../../../../backend/src/test/resources/fixtures/agent/c/plan-card.json';
import questionCard from '../../../../backend/src/test/resources/fixtures/agent/c/question-card.json';
import {
  AgentContractError,
  parseAgentEvent,
  parseAgentMessagePage,
  parseAgentRunSnapshot,
  parseAgentSession,
  parseAgentSessionPage,
  parseAgentActionConfirmationResult,
  validateAgentCardEvent,
} from './contract';

describe('Agent DTO 和事件校验', () => {
  it('直接消费最新会话、历史和运行夹具', () => {
    expect(parseAgentSession(sessionCreated.data).sessionId).toBe('session-example-2');
    expect(parseAgentSessionPage(sessionList.data).records).toHaveLength(1);
    expect(parseAgentMessagePage(messageHistory.data).records[0].messageId).toBe(
      'message-example-2',
    );
    expect(parseAgentMessagePage(messageHistory.data).records[0].runId).toBe(
      '52b810c5-4b03-4a41-9c36-07372f1a6f59',
    );
    expect(parseAgentRunSnapshot(runCompleted.data).lastEventId).toBe('42');
  });

  it('校验确认卡并解析确认结果', () => {
    expect(validateAgentCardEvent(parseAgentEvent(orderConfirmCard)).decision).toBe('render');
    expect(parseAgentActionConfirmationResult(confirmationFixtures.success.data)).toMatchObject({
      actionId: 'action-1',
      status: 'SUCCEEDED',
    });
  });

  it('直接校验 B 正式问题卡和方案卡夹具', () => {
    expect(validateAgentCardEvent(parseAgentEvent(questionCard)).decision).toBe('render');
    expect(validateAgentCardEvent(parseAgentEvent(planCard)).decision).toBe('render');
  });

  it('严格拒绝方案卡缺字段、未知字段和原始工具参数', () => {
    expect(
      validateAgentCardEvent(
        parseAgentEvent({
          ...planCard,
          payload: { ...planCard.payload, schemaVersion: undefined },
        }),
      ).decision,
    ).toBe('reject');
    expect(
      validateAgentCardEvent(
        parseAgentEvent({
          ...planCard,
          payload: { ...planCard.payload, internalEvidence: 'hidden' },
        }),
      ).decision,
    ).toBe('reject');
    expect(
      validateAgentCardEvent(
        parseAgentEvent({
          ...planCard,
          payload: {
            ...planCard.payload,
            plans: [{ ...planCard.payload.plans[0], rawToolArguments: '{}' }],
          },
        }),
      ).decision,
    ).toBe('reject');
  });

  it('正式方案缺少影片名或问题选项值时拒绝渲染', () => {
    const invalidPlan = {
      ...planCard,
      payload: {
        ...planCard.payload,
        plans: [{ ...planCard.payload.plans[0], movieName: undefined }],
      },
    };
    const invalidQuestion = {
      ...questionCard,
      payload: {
        ...questionCard.payload,
        options: [{ ...questionCard.payload.options[0], value: undefined }],
      },
    };
    expect(validateAgentCardEvent(parseAgentEvent(invalidPlan)).decision).toBe('reject');
    expect(validateAgentCardEvent(parseAgentEvent(invalidQuestion)).decision).toBe('reject');
  });

  it('合法事件忽略未知顶层字段但不把它带入投影', () => {
    const event = parseAgentEvent({ ...duplicateEvent, secret: 'do-not-copy' });
    expect(event.eventId).toBe('43');
    expect(event).not.toHaveProperty('secret');
  });

  it.each([
    { ...duplicateEvent, eventId: '1.2' },
    { ...duplicateEvent, eventId: '-1' },
    { ...duplicateEvent, eventId: '0' },
    { ...duplicateEvent, eventId: 53 },
    { ...duplicateEvent, sessionId: '../other' },
    { ...duplicateEvent, runId: '' },
    { ...duplicateEvent, payload: null },
    { ...duplicateEvent, payload: {} },
  ])('拒绝非法 ID 或空 payload', (value) => {
    expect(() => parseAgentEvent(value)).toThrow(AgentContractError);
  });

  it('未知事件和未知 payload 类型由投影安全降级，已知类型缺字段被拒绝', () => {
    const base = {
      ...duplicateEvent,
      eventId: '50',
      planId: null,
      planVersion: null,
      nodeId: null,
      occurredAt: '2026-08-05T10:00:09+08:00',
      displayText: '未来内容',
    };
    expect(
      validateAgentCardEvent(
        parseAgentEvent({
          ...base,
          eventType: 'card.future',
          payload: { type: 'TEXT', text: 'x' },
        }),
      ).decision,
    ).toBe('safe-text');
    expect(
      validateAgentCardEvent(
        parseAgentEvent({ ...base, eventType: 'card', payload: { type: 'FUTURE_CARD' } }),
      ).decision,
    ).toBe('safe-text');
    expect(
      validateAgentCardEvent(
        parseAgentEvent({ ...base, eventType: 'card', payload: { type: 'TEXT' } }),
      ).decision,
    ).toBe('reject');
  });

  it('直接消费 B 正式非法位置授权夹具并拒绝', () => {
    expect(validateAgentCardEvent(parseAgentEvent(invalidLocationAuthorization)).decision).toBe(
      'reject',
    );
  });

  it('超过 JavaScript 安全整数的十进制 ID 保持字符串', () => {
    const event = parseAgentEvent({ ...duplicateEvent, eventId: '9007199254740993' });
    expect(event.eventId).toBe('9007199254740993');
    expect(typeof event.eventId).toBe('string');
  });
});
