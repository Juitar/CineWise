package com.miaoyu.ticket.content.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.content.application.ContentProvider;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.application.LiveContentSyncPort;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 学习/演示环境的候选真实内容 Provider。
 *
 * <p>本类只建立受控调用和标准化入口，尚不接入页面查询或同步写入；第 3 章完成后才可把合格结果
 * 写入真实快照。这样关闭开关、生产环境或字段不合格时，现有 Demo 回退不会受到影响。</p>
 */
public final class NetStartContentProvider implements ContentProvider, LiveContentSyncPort {
    private static final String PROVIDER = "NETSTART_MAOYAN";
    private final NetStartProperties properties;
    private final Environment environment;
    private final Clock clock;
    private final NetStartRawClient rawClient;
    private final NetStartContentMapper mapper = new NetStartContentMapper();
    private final Deque<Instant> requestTimes = new ArrayDeque<>();

    public NetStartContentProvider(NetStartProperties properties, Environment environment, Clock clock,
                                   NetStartRawClient rawClient) {
        this.properties = properties;
        this.environment = environment;
        this.clock = clock;
        this.rawClient = rawClient;
    }

    /**
     * 未显式开启、非开发/演示环境或尚未接入同步 HTTP 客户端时都返回空结果。
     *
     * <p>页面不会调用此 Provider；空结果迫使后续同步用例显式提供经过限流、超时和重试控制的原始响应，
     * 防止开发人员绕过质量校验直接把第三方 JSON 暴露出去。</p>
     */
    @Override
    public Optional<ContentResult<List<? extends ContentItem>>> query(ContentQuery query) {
        if (!properties.enabled() || !isLearningEnvironment() || !hasSupportedInput(query) || !allowRequest()) {
            return Optional.empty();
        }
        try {
            return normalizeAll(query, rawClient.fetch(query));
        } catch (ResourceAccessException | RestClientResponseException exception) {
            if (!isRetryable(exception) || !allowRequest()) {
                return Optional.empty();
            }
            try {
                Thread.sleep(properties.retryBackoff());
                return normalizeAll(query, rawClient.fetch(query));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (ResourceAccessException | RestClientResponseException ignored) {
                return Optional.empty();
            }
        }
    }

    /** 将已经受控取得的单条原始响应转换为业务可用的真实来源封套，供同步用例使用。 */
    Optional<ContentResult<List<? extends ContentItem>>> normalize(ContentQuery query, JsonNode raw) {
        if (!properties.enabled() || !isLearningEnvironment()) {
            return Optional.empty();
        }
        return mapper.map(query.resourceType(), raw, query.cityCode()).map(item -> {
            LocalDateTime dataTime = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
            return new ContentResult<>(List.of(item), new ContentSource(PROVIDER, ContentSourceType.LIVE), dataTime,
                    dataTime.plusHours(6), false, false, null);
        });
    }

    private Optional<ContentResult<List<? extends ContentItem>>> normalizeAll(ContentQuery query, JsonNode raw) {
        List<ContentItem> items = mapper.mapAll(query.resourceType(), raw, query.cityCode());
        if (items.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime dataTime = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        return Optional.of(new ContentResult<>(items, new ContentSource(PROVIDER, ContentSourceType.LIVE), dataTime,
                dataTime.plusHours(6), false, false, null));
    }

    /** 影院搜索必须带城市和关键词；影片详情或公开热映列表不接受任何票务参数。 */
    private boolean hasSupportedInput(ContentQuery query) {
        return query.resourceType() != com.miaoyu.ticket.content.domain.ContentResourceType.CINEMA
                || (query.cityCode() != null && query.keyword() != null);
    }

    /** 滑动一分钟窗口只保护本进程；到达阈值直接回退，绝不排队扩大对第三方的压力。 */
    private synchronized boolean allowRequest() {
        Instant now = clock.instant();
        while (!requestTimes.isEmpty() && !requestTimes.peekFirst().plusSeconds(60).isAfter(now)) {
            requestTimes.removeFirst();
        }
        if (requestTimes.size() >= properties.requestsPerMinute()) {
            return false;
        }
        requestTimes.addLast(now);
        return true;
    }

    private boolean isRetryable(Exception exception) {
        return exception instanceof ResourceAccessException || exception instanceof RestClientResponseException response
                && response.getStatusCode().is5xxServerError();
    }

    private boolean isLearningEnvironment() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equals(profile) || "demo".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 每日同步先从热映列表取得外部 ID，再逐部请求详情补齐最低字段；影院使用已验证的长沙 ci=70。
     *
     * <p>热映列表本身缺少类型和片长，因此绝不直接保存。单条失败只跳过该条，保留其余合格内容，
     * 由上层审计记录本轮统计。</p>
     */
    @Override
    public List<SynchronizedContent> fetchForDailySync() {
        if (!properties.enabled() || !isLearningEnvironment()) {
            return List.of();
        }
        java.util.ArrayList<SynchronizedContent> synchronizedContent = new java.util.ArrayList<>();
        try {
            JsonNode hotList = rawClient.fetch(new ContentQuery(
                    com.miaoyu.ticket.content.domain.ContentResourceType.MOVIE, null, null, null));
            for (JsonNode movie : hotList.path("movieList")) {
                if (!movie.path("id").canConvertToLong()) { continue; }
                ContentQuery query = new ContentQuery(com.miaoyu.ticket.content.domain.ContentResourceType.MOVIE,
                        movie.path("id").longValue(), null, null);
                query(query).ifPresent(result -> synchronizedContent.add(new SynchronizedContent(query, result)));
            }
            ContentQuery cinemas = new ContentQuery(com.miaoyu.ticket.content.domain.ContentResourceType.CINEMA,
                    null, "70", "影");
            query(cinemas).ifPresent(result -> synchronizedContent.add(new SynchronizedContent(cinemas, result)));
        } catch (RuntimeException ignored) {
            // 外部源不可用时本轮不写半截数据；页面继续走既有真实快照和 Demo 回退。
        }
        return List.copyOf(synchronizedContent);
    }
}
