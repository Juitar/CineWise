package com.miaoyu.ticket.agent.application.reply;

/**
 * 只读工具仍在处理时返回的安全进度事实。
 *
 * <p>它只说明哪个计划节点没有得到最终结果，不包含轮询地址、重试次数或下游异常。当前最小流程在
 * PROCESSING 时立即结束本轮，因此调用方不能把该消息理解为“已生成推荐”。
 *
 * @param nodeId 状态机已选择的节点标识，用于关联本次运行快照而不是作为外部 API 主键
 */
public record ProgressReplyFacts(String nodeId) implements AgentReplyPayload {

    public ProgressReplyFacts {
        // 无节点标识的进度无法追溯到计划步骤，容易让后续 SSE 层错误合并不同运行。
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId 不能为空");
        }
    }

    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.PROGRESS;
    }
}
