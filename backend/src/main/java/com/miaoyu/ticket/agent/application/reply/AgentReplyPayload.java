package com.miaoyu.ticket.agent.application.reply;

/**
 * 回复模型只接收这些 B 自有的类型化事实，不接收任意动态字段。
 *
 * <p>sealed 限制使新增回复事实必须在编译期显式登记，避免某个调用方传入包含异常对象、用户资料或
 * D 内部 DTO 的任意 Map。模型只需要展示事实，不应获得更宽的运行上下文。
 */
public sealed interface AgentReplyPayload
        permits ErrorReplyFacts, ProgressReplyFacts, QuestionReplyFacts, RecommendationReplyFacts {

    /**
     * 判断当前载荷是否允许用于指定回复类型。
     *
     * <p>此方法是回复边界的最后一层类型检查；它拒绝不匹配的组合，而不是尝试把事实自动转换成
     * 另一种卡片，防止错误状态被展示为推荐结果。
     */
    boolean supports(AgentReplyMessageType messageType);
}
