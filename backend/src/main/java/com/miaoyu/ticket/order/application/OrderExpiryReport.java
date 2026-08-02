package com.miaoyu.ticket.order.application;

/** 一批过期订单释放的可观测结果。 */
public record OrderExpiryReport(int scannedCount, int expiredCount, int skippedCount, int failedCount) {
}
