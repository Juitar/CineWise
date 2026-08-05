package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * MyBatis读取REFUNDED出行取消补偿候选的最小行投影。
 *
 * <p>refundedAt既是原退款发生时间，也是键集分页第一排序键；orderVersion供应用层
 * 重读订单后识别并发变化。高频扫描不读取金额、座位、票或退款请求详情。</p>
 *
 * @param orderId A的内部订单主键，也是D任务的唯一业务关联
 * @param userId 失效事件所需的权威用户标识
 * @param showId 失效事件所需的权威场次标识
 * @param orderVersion 扫描时观察到的退款完成版本
 * @param refundedAt 原始退款完成时间
 */
public record RefundedTravelReconciliationCandidateRow(
        long orderId,
        long userId,
        long showId,
        int orderVersion,
        LocalDateTime refundedAt) {
}
