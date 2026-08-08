package com.miaoyu.ticket.agent.application.reply;

/**
 * 缺少下一项必填槽位时使用的最小追问事实。
 *
 * <p>问题只携带面向用户的语义，不能把工具槽位名或业务 ID 传入模型、持久化消息或 SSE。当前一轮
 * 只询问一个内容，调用方才能将下一次用户回复稳定写入同一个服务端槽位快照。
 *
 * @param kind 用户需要补充的受控语义
 * @param inputLabel 输入框展示名称
 */
public record QuestionReplyFacts(QuestionKind kind, String inputLabel) implements AgentReplyPayload {

    public QuestionReplyFacts {
        if (kind == null || inputLabel == null || inputLabel.isBlank()) {
            throw new IllegalArgumentException("问题语义和输入名称不能为空");
        }
    }

    /** 只将已确认可由用户正常回答的工具槽位转换为问题语义。 */
    public static QuestionReplyFacts fromToolSlot(String slotName) {
        return switch (slotName) {
            case "cityCode" -> new QuestionReplyFacts(QuestionKind.CITY, "城市");
            case "date" -> new QuestionReplyFacts(QuestionKind.DATE, "日期");
            case "ticketCount" -> new QuestionReplyFacts(QuestionKind.TICKET_COUNT, "人数");
            default -> throw new IllegalArgumentException("不允许向用户追问该字段");
        };
    }

    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.QUESTION;
    }

    public enum QuestionKind {
        CITY,
        DATE,
        TICKET_COUNT
    }
}
