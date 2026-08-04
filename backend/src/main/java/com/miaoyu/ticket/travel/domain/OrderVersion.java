package com.miaoyu.ticket.travel.domain;

/**
 * A 提供的订单版本号。
 *
 * <p>D 只比较该值来判断失效事件是否比任务记录的新，不能读取订单表补齐状态。
 * 将它单独建模能避免把订单版本与任务自身版本混用。</p>
 *
 * @param value 从零开始的订单版本
 */
public record OrderVersion(long value) {

    /**
     * 订单版本与迁移中的非负约束一致，负数事件在进入后续取消逻辑前必须被拒绝。
     */
    public OrderVersion {
        if (value < 0) {
            throw new IllegalArgumentException("orderVersion 不能为负数");
        }
    }

    /**
     * 判断当前版本是否不早于另一版本。
     *
     * <p>退款事件只有版本不小于任务已记录版本时才能取消任务；这个比较方法不决定
     * 具体取消动作，避免在基础类型中提前实现 2.4 的业务流程。</p>
     *
     * @param other 要比较的已有订单版本
     * @return 当前版本不小于 {@code other} 时返回 {@code true}
     */
    public boolean isAtLeast(OrderVersion other) {
        return value >= other.value;
    }
}
