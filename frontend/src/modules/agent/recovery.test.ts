import { describe, expect, it, vi } from 'vitest';

import messageHistory from '../../../../backend/src/test/resources/fixtures/agent/c/session-message-history.json';
import planCard from '../../../../backend/src/test/resources/fixtures/agent/c/plan-card.json';
import runCompleted from '../../../../backend/src/test/resources/fixtures/agent/c/run-completed.json';
import streamReset from '../../../../backend/src/test/resources/fixtures/agent/c/stream-reset.json';
import { parseAgentEvent, parseAgentMessagePage, parseAgentRunSnapshot } from './contract';
import { recoverFromStreamReset } from './recovery';

describe('stream.reset 恢复', () => {
  it('先读取运行和历史，并在 43/42 时继续使用会话水位线 43', async () => {
    const getRun = vi.fn(async () => parseAgentRunSnapshot(runCompleted.data));
    const getMessages = vi.fn(async () => parseAgentMessagePage(messageHistory.data));
    const result = await recoverFromStreamReset(parseAgentEvent(streamReset), {
      getRun,
      getMessages,
    });
    expect(getRun).toHaveBeenCalledWith('run-example-1');
    expect(getMessages).toHaveBeenCalledWith('session-example-1');
    expect(result.lastEventId).toBe('43');
    expect(result.runId).toBe('run-example-1');
    expect(result.items).toContainEqual(expect.objectContaining({ text: '步骤已完成' }));
  });

  it('水位线不一致时停止且不发送任何 GET', async () => {
    const getRun = vi.fn();
    const getMessages = vi.fn();
    await expect(
      recoverFromStreamReset(parseAgentEvent({ ...streamReset, payload: { watermark: '44' } }), {
        getRun,
        getMessages,
      }),
    ).rejects.toThrow('恢复水位线不一致');
    expect(getRun).not.toHaveBeenCalled();
    expect(getMessages).not.toHaveBeenCalled();
  });

  it('超大 reset 水位线仍保持字符串', async () => {
    const watermark = '9007199254740993';
    const result = await recoverFromStreamReset(
      parseAgentEvent({ ...streamReset, eventId: watermark, payload: { watermark } }),
      {
        getRun: async () => parseAgentRunSnapshot(runCompleted.data),
        getMessages: async () => parseAgentMessagePage(messageHistory.data),
      },
    );
    expect(result.lastEventId).toBe(watermark);
  });

  it('reset 快照包含完整卡片时原子恢复卡片，但继续使用会话 watermark', async () => {
    const snapshot = parseAgentRunSnapshot({
      ...runCompleted.data,
      events: [
        {
          ...planCard,
          eventId: '42',
          eventType: 'card',
          sessionId: 'session-example-1',
          runId: 'run-example-1',
          occurredAt: '2026-08-05T10:00:02+08:00',
          planId: 'plan-example-1',
          planVersion: 1,
          nodeId: 'render-plan',
          displayText: '推荐方案',
        },
      ],
    });
    const result = await recoverFromStreamReset(parseAgentEvent(streamReset), {
      getRun: async () => snapshot,
      getMessages: async () => parseAgentMessagePage(messageHistory.data),
    });
    expect(result.lastEventId).toBe('43');
    expect(result.items).toContainEqual(
      expect.objectContaining({ kind: 'plan-card', title: '推荐场次' }),
    );
  });
});
