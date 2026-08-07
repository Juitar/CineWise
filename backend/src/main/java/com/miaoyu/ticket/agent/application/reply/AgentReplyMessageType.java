package com.miaoyu.ticket.agent.application.reply;

/**
 * 当前最小只读主控能够生成的内部回复类型。
 *
 * <p>这些值是 B 内部回复语义，不是前端事件名，也不能据此推断订单或支付状态。新增类型必须先定义
 * 对应的 {@link AgentReplyPayload} 实现和主控处理分支，不能只给枚举增加常量。
 */
public enum AgentReplyMessageType {
    /** 不执行 Tool 的普通、安全文本回复。 */
    TEXT,
    /** 仅用于要求补齐一个服务端白名单中的必填槽位。 */
    QUESTION,
    /** 已取得至少一个可购买场次时的推荐计划卡。 */
    PLAN_CARD,
    /** 已校验的本人出行建议摘要。 */
    TRAVEL_ADVICE_CARD,
    /** 已确定进入选座步骤，但座位业务 DTO 仍由 A 的公开接口定义。 */
    SELECT_SEATS,
    /** 查询成功但当前没有可购买场次时的降级影片卡。 */
    MOVIE_CARD,
    /** 下游工具结果尚未确定时的进度提示，不代表请求最终成功。 */
    PROGRESS,
    /** 计划校验或工具执行无法完成时的稳定错误回复。 */
    ERROR
}
