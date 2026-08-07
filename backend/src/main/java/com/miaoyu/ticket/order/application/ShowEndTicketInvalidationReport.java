package com.miaoyu.ticket.order.application;

/** 单轮场次结束电子票处理结果，只用于任务日志和监控。 */
public record ShowEndTicketInvalidationReport(
        int scannedCount,
        int invalidatedCount,
        int skippedCount,
        int failedCount) {
}
