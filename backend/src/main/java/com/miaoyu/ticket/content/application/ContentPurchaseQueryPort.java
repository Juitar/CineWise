package com.miaoyu.ticket.content.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
     * <p>空输入返回空 Map；未找到或不可展示的 ID 不进入结果，由 A 排除对应排期，不得补写标题或来源。
     * 内容目录整体不可用时抛出业务码 303004，由公开接口返回 HTTP 503，不能伪装成影院没有可售影片。</p>
     */
    Map<Long, MovieSummary> findMovieSummaries(Set<Long> movieIds);

    /**
     * 查找长沙真实内容中可用于演示购票的一家影院和最多三部影片。
     *
     * <p>没有完整 LIVE 内容时返回空，调用方继续保留固定 Demo 种子，不得把 Mock 内容冒充真实影院。</p>
     */
    Optional<ContentSeedCatalog> findChangshaLivePurchaseCatalog();

    /**
     * 返回指定城市可用于 dev/demo 排期初始化的真实内容目录。
     *
     * <p>目录只接受未过期的 {@code LIVE/NETSTART_MAOYAN} 资料，绝不混入 Demo、缓存或历史资料；
     * 没有合格资料时返回空列表，内容存储不可读时抛出 303004。A 只能据此创建自己的 demo-seed，
     * 不得把返回的资料解释为真实票价、座位或场次。</p>
     */
    DemoPurchaseCatalog findLiveDemoPurchaseCatalog(String cityCode);

    /** A 初始化演示排期所需的最小真实内容目录，不暴露 D 的持久化对象。 */
    record DemoPurchaseCatalog(
            java.util.List<MovieRef> movies,
            java.util.List<CinemaRef> cinemas,
            String source,
            OffsetDateTime dataAt,
            OffsetDateTime expiresAt) {
        public DemoPurchaseCatalog {
            movies = java.util.List.copyOf(movies);
            cinemas = java.util.List.copyOf(cinemas);
            if (!"NETSTART_MAOYAN".equals(source)) {
                throw new IllegalArgumentException("DemoPurchaseCatalog source must be NETSTART_MAOYAN");
            }
            java.util.Objects.requireNonNull(dataAt, "dataAt must not be null");
            java.util.Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }

    /** 影片引用只保留 A 生成本地 Mock 排期必需的稳定身份和时长。 */
    record MovieRef(long movieId, String sourceMovieId, int durationMinutes) {
        public MovieRef {
            if (movieId <= 0 || durationMinutes <= 0 || sourceMovieId == null || sourceMovieId.isBlank()) {
                throw new IllegalArgumentException("MovieRef must contain positive IDs, positive duration and source ID");
            }
            sourceMovieId = sourceMovieId.trim();
        }
    }

    /** 影院引用只保留 A 生成本地 Mock 排期必需的稳定身份。 */
    record CinemaRef(long cinemaId, String sourceCinemaId) {
        public CinemaRef {
            if (cinemaId <= 0 || sourceCinemaId == null || sourceCinemaId.isBlank()) {
                throw new IllegalArgumentException("CinemaRef must contain a positive ID and source ID");
            }
            sourceCinemaId = sourceCinemaId.trim();
        }
    }

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
