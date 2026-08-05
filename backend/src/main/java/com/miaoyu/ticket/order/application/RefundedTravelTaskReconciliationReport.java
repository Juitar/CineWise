package com.miaoyu.ticket.order.application;

/**
 * 一轮REFUNDED出行任务取消补偿的无敏感信息汇总。
 *
 * <ul>
 *   <li>scannedCount统计数据库候选，不等同于实际跨模块调用数；</li>
 *   <li>ensuredCount表示D已接受取消确保调用，包含重复调用返回既有终态；</li>
 *   <li>skippedCount表示状态、版本或上下文不再满足条件；</li>
 *   <li>failedCount保留给后续轮次重试，不会回滚其他候选。</li>
 * </ul>
 *
 * @param batchCount 本轮非空数据库批次数
 * @param scannedCount 本轮读取候选订单总数
 * @param ensuredCount 成功调用D取消确保入口的订单数
 * @param skippedCount 本轮主动跳过且未调用D的订单数
 * @param failedCount 捕获异常并保留重试机会的订单数
 */
public record RefundedTravelTaskReconciliationReport(
        int batchCount,
        int scannedCount,
        int ensuredCount,
        int skippedCount,
        int failedCount) {
}
