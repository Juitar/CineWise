import { AgentContractError } from './contract';
import { buildProjectionFromSnapshot } from './projection';
import type { AgentProjection } from './projection';
import type { AgentEvent, AgentMessagePage, AgentRunSnapshot } from './types';

export interface ResetRecoveryDependencies {
  getRun(runId: string): Promise<AgentRunSnapshot>;
  getMessages(sessionId: string): Promise<AgentMessagePage>;
}

export function getResetWatermark(event: AgentEvent): string {
  const watermark = event.payload.watermark;
  if (
    typeof watermark !== 'string' ||
    !/^(0|[1-9]\d*)$/.test(watermark) ||
    watermark !== event.eventId
  ) {
    throw new AgentContractError('恢复水位线不一致，已停止自动恢复');
  }
  return watermark;
}

/** reset 只读查询运行和历史，然后一次返回替换投影；不会重发消息。 */
export async function recoverFromStreamReset(
  event: AgentEvent,
  dependencies: ResetRecoveryDependencies,
): Promise<AgentProjection> {
  if (event.eventType !== 'stream.reset') throw new AgentContractError();
  const watermark = getResetWatermark(event);
  const [snapshot, history] = await Promise.all([
    dependencies.getRun(event.runId),
    dependencies.getMessages(event.sessionId),
  ]);
  if (snapshot.runId !== event.runId || snapshot.sessionId !== event.sessionId) {
    throw new AgentContractError('恢复数据不属于当前会话');
  }
  return { ...buildProjectionFromSnapshot(snapshot, history.records), lastEventId: watermark };
}
