package com.miaoyu.ticket.content.application;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * D 提供给票务和演示种子编排的公开内容查询边界。
 *
 * <p>A 只能通过该端口取得影片展示摘要和已标准化的内容 ID，不能访问 D 的 Controller、缓存、
 * Mapper 或内容表。这样排片可以引用真实影院资料，同时仍明确保持为 A 管理的演示票务数据。</p>
 */
public interface ContentPurchaseQueryPort {

    /**
     * 批量取得最多 100 个影片展示摘要。
     *
     * <p>空输入返回空 Map；未找到或不可展示的 ID 不进入结果；内容目录整体不可用时返回空 Map，
     * 由 A 排除所有缺失摘要的排期，不得补写标题或来源。</p>
     */
    Map<Long, MovieSummary> findMovieSummaries(Set<Long> movieIds);

    /**
     * 查找长沙真实内容中可用于演示购票的一家影院和最多三部影片。
     *
     * <p>没有完整 LIVE 内容时返回空，调用方继续保留固定 Demo 种子，不得把 Mock 内容冒充真实影院。</p>
     */
    Optional<ContentSeedCatalog> findChangshaLivePurchaseCatalog();

    /** 票务页面只获得公开展示摘要和内容时效，不获得 D 的内部内容对象。 */
    record MovieSummary(
            long movieId,
            String title,
            String posterUrl,
            String contentSource,
            LocalDateTime contentDataTime) {

        public MovieSummary {
            if (movieId <= 0) {
                throw new IllegalArgumentException("movieId must be positive");
            }
            title = requireText(title, "title");
            contentSource = requireText(contentSource, "contentSource");
            contentDataTime = Objects.requireNonNull(contentDataTime, "contentDataTime must not be null");
        }

        private static String requireText(String value, String fieldName) {
            String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return normalized;
        }
    }
}
