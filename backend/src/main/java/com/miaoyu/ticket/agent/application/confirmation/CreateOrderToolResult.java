package com.miaoyu.ticket.agent.application.confirmation;

/** A 建单工具可安全交给 Agent 保存的最小结果引用，完整订单仍由 A 的查询接口负责。 */
public record CreateOrderToolResult(String orderReference) {
    public CreateOrderToolResult {
        if (orderReference == null || orderReference.isBlank()) {
            throw new IllegalArgumentException("orderReference 不能为空");
        }
    }
}
