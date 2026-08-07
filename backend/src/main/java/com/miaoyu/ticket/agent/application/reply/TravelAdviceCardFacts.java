package com.miaoyu.ticket.agent.application.reply;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 可持久化和展示的出行建议安全摘要，不依赖 D 的 Tool 或持久化 DTO。 */
public record TravelAdviceCardFacts(
        String taskId, String taskStatus, boolean available, Weather weather, List<Advice> advice, String source,
        boolean degraded, String fallbackType, Instant dataAt, Instant expiresAt, boolean expired)
        implements AgentReplyPayload {
    public TravelAdviceCardFacts {
        requireText(taskId, "taskId");
        requireText(taskStatus, "taskStatus");
        requireText(source, "source");
        advice = List.copyOf(Objects.requireNonNull(advice, "advice 不能为空"));
        if (!taskId.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("taskId 必须是正十进制字符串");
        }
        if (!available && (weather != null || !advice.isEmpty() || degraded || fallbackType != null
                || dataAt != null || expiresAt != null)) {
            throw new IllegalArgumentException("无建议卡片不能包含快照事实");
        }
        if (available && ((dataAt == null) != (expiresAt == null))) {
            throw new IllegalArgumentException("建议时效字段必须同时存在或为空");
        }
        if (dataAt != null && !expiresAt.isAfter(dataAt)) {
            throw new IllegalArgumentException("expiresAt 必须晚于 dataAt");
        }
        if (degraded && (fallbackType == null || fallbackType.isBlank())) {
            throw new IllegalArgumentException("降级建议必须说明 fallbackType");
        }
        if (!degraded && fallbackType != null) {
            throw new IllegalArgumentException("非降级建议不能包含 fallbackType");
        }
    }

    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.TRAVEL_ADVICE_CARD;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    public record Weather(String area, String condition, String risk) {
        public Weather {
            requireNullableText(area, "weather.area");
            requireNullableText(condition, "weather.condition");
            requireNullableText(risk, "weather.risk");
        }
    }

    public record Advice(String type, String text) {
        public Advice {
            requireText(type, "advice.type");
            requireText(text, "advice.text");
        }
    }

    private static void requireNullableText(String value, String name) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能是空白字符串");
        }
    }
}
