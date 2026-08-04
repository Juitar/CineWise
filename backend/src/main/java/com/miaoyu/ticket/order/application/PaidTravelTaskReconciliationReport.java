package com.miaoyu.ticket.order.application;

/**
 * 一轮PAID出行任务补偿的汇总结果。
 *
 * <p>报告只包含计数，避免调度日志收集用户、影院区域或完整事件内容。</p>
 *
 * <ul>
 *   <li>batchCount是发生过候选读取的非空页数，空查询不计为一页；</li>
 *   <li>scannedCount是数据库候选数，不等于实际调用D的次数；</li>
 *   <li>ensuredCount同时包含新建任务和D返回既有唯一任务；</li>
 *   <li>skippedCount表示状态、版本或上下文不再满足补偿条件；</li>
 *   <li>failedCount表示单条异常且仍需要后续轮次重试。</li>
 * </ul>
 *
 * @param batchCount 本轮处理的非空数据库批次数
 * @param scannedCount 本轮读取的候选订单总数
 * @param ensuredCount 成功调用D幂等入口的订单数
 * @param skippedCount 本轮主动跳过且未调用D的订单数
 * @param failedCount 捕获异常并保留重试机会的订单数
 */
public record PaidTravelTaskReconciliationReport(
        int batchCount,
        int scannedCount,
        int ensuredCount,
        int skippedCount,
        int failedCount) {
}
