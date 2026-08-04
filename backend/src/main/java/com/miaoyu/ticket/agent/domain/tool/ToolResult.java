package com.miaoyu.ticket.agent.domain.tool;

import java.time.Instant;
import java.util.Objects;

/**
 * Agent 工具返回的统一公共包装，业务字段仅存放在 data 中。
 *
 * <p>包装明确区分 SUCCESS、PROCESSING 和 FAILED，调用方不得用 data 是否为空猜测状态。降级结果可以
 * 是 SUCCESS，例如无可购场次；因此 degraded 不等同于失败，也不能自动触发重试。
 *
 * @param status 工具执行状态，决定状态机如何推进节点
 * @param data 仅成功结果可供下游业务映射的数据
 * @param errorCode 稳定业务错误码，成功时必须为空
 * @param retryable 工具明确声明可重试时为 true，状态机仍受失败策略和次数限制
 * @param replanSuggested 工具建议外层考虑新计划，不代表状态机自动重规划
 * @param suggestedNextAction 安全的下一步提示，不应包含异常原文或模型提示词
 * @param degraded 是否为公开声明的降级结果
 * @param fallbackType 降级类型，供展示和审计识别数据来源变化
 * @param stateVersion 实际读取的状态/槽位版本
 * @param dataAt 数据产生时间，与 expiresAt 成对出现
 * @param expiresAt 数据过期时间，与 dataAt 成对出现且必须更晚
 */
public record ToolResult<T>(
        ToolStatus status,
        T data,
        Integer errorCode,
        boolean retryable,
        boolean replanSuggested,
        String suggestedNextAction,
        boolean degraded,
        String fallbackType,
        Long stateVersion,
        Instant dataAt,
        Instant expiresAt) {

    public ToolResult {
        Objects.requireNonNull(status, "status 不能为空");
        if (status == ToolStatus.SUCCESS && errorCode != null) {
            // 成功和错误码不能共存，避免调用方既渲染推荐又错误处理同一次工具结果。
            throw new IllegalArgumentException("成功结果不能包含 errorCode");
        }
        if ((dataAt == null) != (expiresAt == null)) {
            // 只给一个时间点无法说明数据有效区间，不能把半截元数据包装成实时结果。
            throw new IllegalArgumentException("dataAt 和 expiresAt 必须同时存在或同时为空");
        }
        if (dataAt != null && !expiresAt.isAfter(dataAt)) {
            // 过期时间必须严格晚于产生时间，零长度窗口对展示和缓存都没有可解释语义。
            throw new IllegalArgumentException("expiresAt 必须晚于 dataAt");
        }
    }

    /** 判断结果是否带有可供后续节点校验的动态数据时效；没有窗口不代表数据永远有效。 */
    public boolean hasFreshnessWindow() {
        return dataAt != null && expiresAt != null;
    }
}
