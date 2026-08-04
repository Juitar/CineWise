package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * MyBatis读取PAID出行补偿候选的最小行投影。
 *
 * <p>该行只承载分页游标和事件重建所需标识；金额、座位与电子票字段不进入高频扫描，
 * paidAt保留原支付发生时间，orderVersion用于应用层重读后的并发校验。</p>
 *
 * @param orderId A的内部订单主键，也是D任务的业务幂等关联
 * @param userId 支付成功事件所需的权威用户标识
 * @param showId 支付成功事件所需的权威场次标识
 * @param orderVersion 扫描时观察到的PAID订单版本
 * @param paidAt 原始支付完成时间和键集分页第一排序键
 */
public record PaidTravelReconciliationCandidateRow(
        long orderId,
        long userId,
        long showId,
        int orderVersion,
        LocalDateTime paidAt) {
}
