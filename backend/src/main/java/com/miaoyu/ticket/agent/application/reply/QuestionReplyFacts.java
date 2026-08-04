package com.miaoyu.ticket.agent.application.reply;

/**
 * 缺少下一项必填槽位时使用的最小追问事实。
 *
 * <p>missingSlot 必须来自服务端 ToolDefinition.requiredInputs，而不是模型生成的任意字段名。当前一轮
 * 只询问一个槽位，调用方才能将下一次用户回复稳定写入同一个服务端槽位快照。
 *
 * @param missingSlot 当前白名单工具仍缺少的字段名，不包含字段值或用户其他资料
 */
public record QuestionReplyFacts(String missingSlot) implements AgentReplyPayload {

    public QuestionReplyFacts {
        // 空字段名会使用户无法补充有效输入，也会给模型留下自行解释追问内容的空间。
        if (missingSlot == null || missingSlot.isBlank()) {
            throw new IllegalArgumentException("missingSlot 不能为空");
        }
    }

    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.QUESTION;
    }
}
