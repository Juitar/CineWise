package com.miaoyu.ticket.recommendation.domain;

import java.time.Instant;
import java.util.Objects;

/** 推荐理由引用的最小可追溯事实，不能保存 A 的库存或第三方原始响应。 */
public record RecommendationEvidence(String field, String value, String source, Instant dataAt, Instant expiresAt) {

    /** 证据必须有来源和有效期，卡片不能把过期事实解释成当前可购信息。 */
    public RecommendationEvidence {
        field = requireText(field, "field");
        value = requireText(value, "value");
        source = requireText(source, "source");
        dataAt = Objects.requireNonNull(dataAt, "dataAt 不能为空");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        if (!expiresAt.isAfter(dataAt)) {
            throw new IllegalArgumentException("expiresAt 必须晚于 dataAt");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
