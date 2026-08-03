package com.miaoyu.ticket.recommendation.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 固定推荐应用结果。
 *
 * <p>无场次时保持成功的内容查询结果，并用 missingFactors 交给主控决定下一步；推荐模块不追问用户，
 * 也不生成 PLAN_CARD。</p>
 */
public record FixedRecommendationResult(
        String algorithmVersion,
        List<RecommendationCandidate> candidates,
        boolean purchaseEligible,
        List<String> missingFactors,
        String source,
        Instant dataAt,
        Instant expiresAt,
        boolean isExpired) {

    // 不可购结果仍保留内容候选，主控可展示后继续询问用户。

    /** 固定目录结果不可被调用方修改，防止同一次运行中候选顺序被意外改变。 */
    public FixedRecommendationResult {
        // 算法版本必须存在，才能让回归用例区分不同固定目录的行为。
        algorithmVersion = requireText(algorithmVersion, "algorithmVersion");
        // 候选和缺失因素都使用不可修改副本，避免 Agent 聚合时篡改领域结果。
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates 不能为空"));
        missingFactors = List.copyOf(Objects.requireNonNull(missingFactors, "missingFactors 不能为空"));
        // 来源必须保留到应用结果，工具适配器后续会映射到公共 ToolResult。
        source = requireText(source, "source");
        // 目录时间窗口成对出现，避免下游误把无时效的结果用于后续写节点。
        dataAt = Objects.requireNonNull(dataAt, "dataAt 不能为空");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        if (!expiresAt.isAfter(dataAt)) {
            throw new IllegalArgumentException("expiresAt 必须晚于 dataAt");
        }
    }

    private static String requireText(String value, String fieldName) {
        // 不接受空字符串，避免把无法展示和无法追溯的字段带到 B 的结果汇总。
        // 此处不做默认值填充，默认来源会掩盖目录配置错误。
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
