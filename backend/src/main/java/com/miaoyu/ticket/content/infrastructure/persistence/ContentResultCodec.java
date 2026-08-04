package com.miaoyu.ticket.content.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 将标准化内容封装成稳定 JSON，缓存和快照不保存 Provider 原始响应。
 *
 * <p>Provider 的原始字段可能随厂商版本变化，也可能包含本模块不该继续保存的字段。这里仅保留 D 的
 * MovieContent、CinemaContent、来源和时间，保证缓存、快照与 Application 层看到的是同一种数据。</p>
 *
 * <p>影片和影院是 sealed 类型，不能让 JSON 直接反序列化抽象接口；因此载荷中显式分为 movies 和
 * cinemas，读取时再由 ContentQuery 的 resourceType 选择。这也能拒绝资源类型混用。</p>
 *
 * <p>序列化失败说明标准化对象已经不满足当前 JSON 契约，应当暴露给调用方并回退，而不是写入无法恢复的
 * 半截缓存或快照。</p>
 */
final class ContentResultCodec {

    private final ObjectMapper objectMapper;

    /** ObjectMapper 由 Spring 统一配置，避免缓存 JSON 与 REST 的时间格式出现两套规则。 */
    ContentResultCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 影片和影院分开保存，恢复时由查询资源类型决定，避免抽象接口反序列化歧义。
     *
     * <p>目录顺序会随 List 原样保存，是演示和固定推荐需要的稳定顺序，不能改成无序集合。</p>
     */
    String write(ContentResult<List<? extends ContentItem>> result) {
        try {
            List<MovieContent> movies = result.data().stream().filter(MovieContent.class::isInstance)
                    .map(MovieContent.class::cast).toList();
            List<CinemaContent> cinemas = result.data().stream().filter(CinemaContent.class::isInstance)
                    .map(CinemaContent.class::cast).toList();
            return objectMapper.writeValueAsString(new StoredContent(
                    result.source(), result.dataTime(), result.expiresAt(), movies, cinemas));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to encode normalized content", exception);
        }
    }

    /**
     * 读取时统一重建回退标识，防止旧缓存把 Demo 或快照误报为直接实时结果。
     *
     * <p>过期状态由业务时钟在上层计算；Codec 不读取当前时间，确保序列化没有环境相关副作用。</p>
     */
    ContentResult<List<? extends ContentItem>> read(String payload, ContentQuery query,
                                                     ContentFallbackType fallbackType, boolean expired) {
        try {
            JsonNode storedNode = objectMapper.readTree(payload);
            // H2 may expose a JSON column as a JSON-encoded string while MySQL returns the object directly.
            // Normalize the JDBC representation before mapping so the snapshot contract stays database-agnostic.
            if (storedNode.isTextual()) {
                storedNode = objectMapper.readTree(storedNode.textValue());
            }
            if (!storedNode.isObject()) {
                throw new IllegalStateException("Normalized content payload must be a JSON object");
            }
            StoredContent stored = objectMapper.treeToValue(storedNode, StoredContent.class);
            List<? extends ContentItem> data = query.resourceType().name().equals("MOVIE")
                    ? stored.movies() : stored.cinemas();
            return new ContentResult<>(data, stored.source(), stored.dataTime(), stored.expiresAt(), expired,
                    true, fallbackType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to decode normalized content", exception);
        }
    }

    /** JSON 只包含内容 DTO 和来源时间，不包含用户、位置、票务或 Redis 元数据。 */
    private record StoredContent(ContentSource source, LocalDateTime dataTime, LocalDateTime expiresAt,
                                 List<MovieContent> movies, List<CinemaContent> cinemas) {
        /** 载荷对象仅作内部持久化格式，不能作为对 A、B、C 的公开 DTO。 */
    }
}
