package com.miaoyu.ticket.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * PAID订单出行任务补偿的有界配置。
 *
 * <p>窗口和批次均设置硬上限，防止配置错误把恢复任务变成无界全表扫描；调度开关和延迟
 * 供测试、单实例演示和后续运维显式控制。退款任务取消补偿尚未交付前，生产默认不得启用
 * 此调度入口。</p>
 *
 * <ul>
 *   <li>enabled只控制定时入口，应用服务仍可由测试或人工恢复流程显式调用；</li>
 *   <li>delayMilliseconds同时作为启动等待和固定延迟，避免启动阶段立即争抢连接；</li>
 *   <li>windowHours限制历史回看范围，超过范围的订单不应由高频Job长期重扫；</li>
 *   <li>batchSize限制单次SQL结果集，不代表一轮最多只处理一页。</li>
 * </ul>
 *
 * @param enabled 是否注册并运行定时补偿入口
 * @param delayMilliseconds 两轮任务完成之间及首次启动前的等待毫秒数
 * @param windowHours 相对任务冻结时间向前扫描的小时数
 * @param batchSize 单次键集分页查询允许返回的最大候选数
 */
@Validated
@ConfigurationProperties(prefix = "cinewise.transaction.paid-travel-reconciliation")
public record PaidTravelReconciliationProperties(
        boolean enabled,
        @Min(60_000) @Max(3_600_000) int delayMilliseconds,
        @Min(1) @Max(168) int windowHours,
        @Min(1) @Max(100) int batchSize) {
}
