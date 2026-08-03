package com.miaoyu.ticket.recommendation.application;

import com.miaoyu.ticket.content.domain.ContentSourceType;
import java.util.Objects;

/**
 * 固定推荐目录的版本和时效配置。
 *
 * <p>目录只表达 D 的固定候选规则，不保存场次、价格、座位或库存，避免把 A 的动态票务事实复制到
 * 推荐模块。</p>
 */
public record FixedRecommendationCatalog(
        String version, String source, ContentSourceType sourceType, long validForMinutes) {

    /**
     * 加载时拒绝损坏目录，保证同一版本的推荐结果可重复。
     *
     * <p>version 用于测试夹具和问题定位；切换候选规则时必须新增版本，而不是复用旧版本名称。</p>
     *
     * <p>source 和 sourceType 会传到结果中，让 B/C 能区分固定演示结果与后续真实数据。</p>
     */
    public FixedRecommendationCatalog {
        version = requireText(version, "version");
        source = requireText(source, "source");
        sourceType = Objects.requireNonNull(sourceType, "sourceType 不能为空");
        if (validForMinutes <= 0L) {
            throw new IllegalArgumentException("validForMinutes 必须大于 0");
        }
    }

    private static String requireText(String value, String fieldName) {
        // 空版本或空来源会使同一候选的来源无法追溯，不能在调用时再宽松处理。
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        // 统一去除首尾空格，避免资源文件的格式差异影响固定结果比较。
        return value.trim();
    }
}
