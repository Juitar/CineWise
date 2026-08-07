package com.miaoyu.ticket.travel.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 供 Agent 渲染的出行建议公开结果。
 *
 * <p>这是 Tool 专用 DTO，不包含快照内部 JSON；B 可以直接使用天气对象和建议数组，
 * 无需了解 D 的持久化字段或兼容期字符串格式。</p>
 */
public record TravelAdviceToolResult(
        boolean available,
        String taskId,
        String taskStatus,
        Weather weather,
        List<Advice> advice,
        String source,
        OffsetDateTime dataAt,
        OffsetDateTime expiresAt,
        boolean expired,
        boolean degraded,
        String fallbackType) {

    public TravelAdviceToolResult {
        advice = List.copyOf(advice == null ? List.of() : advice);
    }

    /** 天气不可用时整体为 null，不能构造看似真实的占位天气。 */
    public record Weather(String area, String condition, String risk) {
    }

    /** 建议类别由 D 固定为 WEATHER 或 TRANSPORT，文本已完成服务端清洗。 */
    public record Advice(String type, String text) {
    }
}
