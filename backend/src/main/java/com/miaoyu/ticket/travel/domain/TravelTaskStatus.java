package com.miaoyu.ticket.travel.domain;

/**
 * 出行任务在 D 模块内的生命周期状态。
 *
 * <p>任务状态只表达建议和提醒的处理进度，不替代订单状态，也不表达邮件投递结果。
 * 订单退款后的取消、提醒投递未知等规则会在后续用例中基于此状态实现。</p>
 */
public enum TravelTaskStatus {
    /** 已根据支付成功创建任务，尚未生成建议。 */
    PENDING,

    /** 正在生成建议，避免并发请求把同一任务当作可再次生成。 */
    GENERATING,

    /** 已生成可查看的建议，邮件尚未确认投递成功。 */
    READY,

    /** 邮件已确认投递成功，任务仍可供本人查询。 */
    NOTIFIED,

    /** 观影流程已结束，不允许支付成功事件重新打开任务。 */
    COMPLETED,

    /** 订单失效后的墓碑状态，用于拦住乱序到达的旧支付事件。 */
    CANCELLED,

    /** 任务处理已失败并关闭，后续恢复必须走明确的补偿规则。 */
    FAILED;

    /**
     * 判断任务是否已经结束。
     *
     * <p>结束任务必须保留关闭时间，并且不能被迟到的支付成功事件重新置为待处理。</p>
     *
     * @return 已结束时返回 {@code true}
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }

    /**
     * 判断持久化任务时是否必须填写关闭时间。
     *
     * <p>这里与 V007 的状态约束保持同一规则，后续持久化适配器不应自行扩大终态集合。</p>
     *
     * @return 需要 {@code closed_at} 时返回 {@code true}
     */
    public boolean requiresClosedAt() {
        return isTerminal();
    }
}
