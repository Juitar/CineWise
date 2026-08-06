import { describe, expect, it, vi } from 'vitest';

import toolStart from './fixtures/tool-start.json';
import { parseAgentEvent } from './contract';
import { parseSseStream } from './sse';

function chunks(values: readonly string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      values.forEach((value) => controller.enqueue(encoder.encode(value)));
      controller.close();
    },
  });
}

describe('Agent stream parser 新协议', () => {
  it('跨网络分块解析 tool.start，并保留外层 eventId/eventType', async () => {
    const received: unknown[] = [];
    const json = JSON.stringify(toolStart);
    await parseSseStream(
      chunks([`id: 40\nevent: tool.start\ndata: ${json.slice(0, 30)}`, `${json.slice(30)}\n\n`]),
      async (message) => {
        received.push(parseAgentEvent(JSON.parse(message.data)));
      },
      vi.fn(),
    );
    expect(received[0]).toEqual(
      expect.objectContaining({ eventId: '40', eventType: 'tool.start' }),
    );
  });

  it('无 ID 心跳不产生事件，多个事件按顺序解析', async () => {
    const received: string[] = [];
    const heartbeat = vi.fn();
    const data = JSON.stringify(toolStart);
    await parseSseStream(
      chunks([`:heartbeat\n\nid: 40\ndata: ${data}\n\nid: 41\ndata: ${data}\n\n`]),
      (message) => {
        received.push(message.id ?? '');
      },
      heartbeat,
    );
    expect(heartbeat).toHaveBeenCalledOnce();
    expect(received).toEqual(['40', '41']);
  });
});
