import { describe, expect, it } from 'vitest';

import duplicateEvent from '../../../../backend/src/test/resources/fixtures/agent/c/duplicate-event.json';
import invalidLocationAuthorization from '../../../../backend/src/test/resources/fixtures/agent/c/invalid-location-authorization.json';
import messageHistory from '../../../../backend/src/test/resources/fixtures/agent/c/session-message-history.json';
import runCompleted from '../../../../backend/src/test/resources/fixtures/agent/c/run-completed.json';
import sessionCreated from '../../../../backend/src/test/resources/fixtures/agent/c/session-created.json';
import sessionList from '../../../../backend/src/test/resources/fixtures/agent/c/session-list.json';
import {
  AgentContractError,
  parseAgentEvent,
  parseAgentMessagePage,
  parseAgentRunSnapshot,
  parseAgentSession,
  parseAgentSessionPage,
  validateAgentCardEvent,
} from './contract';

describe('Agent DTO 和事件校验', () => {
  it('直接消费最新会话、历史和运行夹具', () => {
    expect(parseAgentSession(sessionCreated.data).sessionId).toBe('session-example-2');
    expect(parseAgentSessionPage(sessionList.data).records).toHaveLength(1);
    expect(parseAgentMessagePage(messageHistory.data).records[0].messageId).toBe(
      'message-example-2',
    );
    expect(parseAgentRunSnapshot(runCompleted.data).lastEventId).toBe('42');
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
