package com.miaoyu.ticket.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * REFUNDED订单出行任务取消补偿的有界配置。
 *
 * <p>窗口、批次和调度周期均限制上限，防止配置错误形成无界扫描。启用开关只控制
 * 定时入口，应用服务仍可在测试或受控恢复流程中显式调用。</p>
 *
 * @param enabled 是否注册REFUNDED定时补偿入口
 * @param delayMilliseconds 首次启动和两轮任务之间的固定延迟
 * @param windowHours 相对冻结任务时间向前扫描的小时数
 * @param batchSize 单次键集分页查询最大候选数
 */
@Validated
@ConfigurationProperties(prefix = "cinewise.transaction.refunded-travel-reconciliation")
public record RefundedTravelReconciliationProperties(
        boolean enabled,
        @Min(60_000) @Max(3_600_000) int delayMilliseconds,
        @Min(1) @Max(168) int windowHours,
        @Min(1) @Max(100) int batchSize) {
}
