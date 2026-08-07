package com.miaoyu.ticket.content.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.application.ContentPersistencePort;
import com.miaoyu.ticket.content.application.LiveContentSyncPort;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 学习/演示环境的候选真实内容 Provider。
 *
 * <p>本类只建立受控调用和标准化入口，尚不接入页面查询或同步写入；第 3 章完成后才可把合格结果
 * 写入真实快照。这样关闭开关、生产环境或字段不合格时，现有 Demo 回退不会受到影响。</p>
 *
 * <p>NetStart 不是票务上游。它只能提供影片、影院基础资料，任何场次、票价、库存、座位、订单、
 * 影评正文或用户数据都不会越过 Mapper 进入 content 模块。</p>
 *
 * <p>本类的 10 req/min 是本应用在学习环境的自我保护值，不代表 NetStart 的公开配额。
 * 计数按本进程一分钟滑动窗口执行，部署多副本前必须另行确认共享限流方案。</p>
 *
 * <p>所有实际 HTTP 请求都集中到 fetchWithPolicy。这样热映列表、影片详情和影院搜索使用相同的
 * 限流、超时和重试规则，新增调用方也不能轻易绕过该边界。</p>
 *
 * <p>连接失败或 5xx 只允许一次短重试；429、其他 4xx、字段不合格和本地限流不重试。
 * 失败不会改变页面读取顺序，页面仍从缓存、快照和固定 Demo 中选择结果。</p>
 *
 * <p>Provider 默认关闭，而且只接受 dev/demo profile。即便存在网络连通性，生产或商业环境
 * 也会拒绝请求，避免把学习来源误当成正式授权的数据源。</p>
 *
 * <p>DailySyncBatch 仅携带已经标准化的结果和脱敏失败分类。Application 层不会依赖 HTTP 状态
 * 或 JsonNode，因此可以分别测试网络策略、字段映射和数据库写入规则。</p>
 *
 * <p>同步不修改固定 Demo 文件，也不会把 Demo 结果写回 Redis，保证离线演示数据保持可预测。</p>
 */
public final class NetStartContentProvider implements LiveContentSyncPort {
    private static final String PROVIDER = "NETSTART_MAOYAN";
    // 对页面和快照使用国家行政区划代码，不能把 NetStart 的 ci 当成业务城市代码。
    private static final String CHANGSHA_CITY_CODE = "430100";
    private final NetStartProperties properties;
    private final Environment environment;
    private final Clock clock;
    private final NetStartRawClient rawClient;
    private final NetStartContentMapper mapper = new NetStartContentMapper();
    private final NetStartRequestLimiter requestLimiter;

    public NetStartContentProvider(NetStartProperties properties, Environment environment, Clock clock,
                                   NetStartRawClient rawClient) {
        this(properties, environment, clock, rawClient, new NetStartRequestLimiter(properties, clock));
    }

    NetStartContentProvider(NetStartProperties properties, Environment environment, Clock clock,
                            NetStartRawClient rawClient, NetStartRequestLimiter requestLimiter) {
        this.properties = properties;
        this.environment = environment;
        this.clock = clock;
        this.rawClient = rawClient;
        this.requestLimiter = requestLimiter;
    }

    /**
     * 未显式开启、非开发/演示环境或尚未接入同步 HTTP 客户端时都返回空结果。
     *
     * <p>页面不会调用此 Provider；空结果迫使后续同步用例显式提供经过限流、超时和重试控制的原始响应，
     * 防止开发人员绕过质量校验直接把第三方 JSON 暴露出去。</p>
     */
    public Optional<ContentResult<List<? extends ContentItem>>> query(ContentQuery query) {
        if (!properties.enabled() || !isLearningEnvironment() || !hasSupportedInput(query)) {
            return Optional.empty();
        }
        RawFetchResult fetched = fetchWithPolicy(query);
        return fetched.payload() == null ? Optional.empty() : normalizeAll(query, fetched.payload());
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

    /**
     * 影院搜索必须带城市和关键词；影片详情或公开热映列表不接受任何票务参数。
     *
     * <p>这里拦住不完整查询，避免把模糊或缺失条件扩散为大范围第三方调用。
     * 它不是前端参数校验的替代，Controller 仍要按公开接口规则校验用户输入。</p>
     */
    private boolean hasSupportedInput(ContentQuery query) {
        return query.resourceType() != com.miaoyu.ticket.content.domain.ContentResourceType.CINEMA
                || (query.cityCode() != null && query.keyword() != null);
    }

    /**
     * 滑动一分钟窗口只保护本进程；到达阈值直接回退，绝不排队扩大对第三方的压力。
     *
     * <p>每次尝试（包括可重试请求）都会先消耗额度，避免重试绕开保护值。
     * 到达阈值后返回空结果，由上层记录 RATE_LIMITED 并继续离线回退。</p>
     */
    private boolean allowRequest() { return requestLimiter.allowRequest(); }

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
     * 每日同步先从热映列表取得外部 ID，再逐部请求详情补齐最低字段；影院公开使用长沙行政代码
     * 430100，HTTP 适配器再转换为已验证的 NetStart ci=70。
     *
     * <p>热映列表本身缺少类型和片长，因此绝不直接保存。单条失败只跳过该条，保留其余合格内容，
     * 由上层审计记录本轮统计。</p>
     *
     * <p>默认每分钟最多十次调用时，热映列表、八个影片详情和一次影院搜索恰好消耗十次额度。
     * 影院请求被预留，不能因为热映列表恰好返回十部影片而总是被挤掉。</p>
     *
     * <p>attemptedCount 只统计尝试标准化的详情和影院内容，不把热映索引当作可保存内容。
     * 这样同步日志的成功、失败计数描述的是实际数据项，而非目录页的行数。</p>
     *
     * <p>一条详情失败不会让已合格的其他资料一起丢失；但失败结果会保留明确分类，
     * 让同步审计能区分限流、连接失败、上游失败与字段不合格。</p>
     */
    @Override
    public DailySyncBatch fetchForDailySync() {
        if (!properties.enabled() || !isLearningEnvironment()) {
            return new DailySyncBatch(List.of(), 0, Outcome.PROVIDER_DISABLED, null);
        }
        java.util.ArrayList<SynchronizedContent> synchronizedContent = new java.util.ArrayList<>();
        int rejectedItemCount = 0;
        Outcome failureOutcome = null;
        Integer failureCode = null;
        ContentQuery hotMovieList = new ContentQuery(
                com.miaoyu.ticket.content.domain.ContentResourceType.MOVIE, null, null, null);
        RawFetchResult hotListResult = fetchWithPolicy(hotMovieList);
        if (hotListResult.payload() == null) {
            // 热映目录请求本身已经发生且失败，审计必须按一条失败内容项记账；否则 V004 会把连接失败误判为
            // total=0 的 SUCCESS，排障人员只能从摘要反推真实结果，无法按结构化状态筛选失败批次。
            return new DailySyncBatch(List.of(), 1, hotListResult.outcome(), hotListResult.errorCode());
        }
        // 热映列表与影院各预留一个请求额度，避免十部详情把本地 10 req/min 用尽后跳过影院。
        int movieDetailLimit = Math.max(0, properties.requestsPerMinute() - 2);
        int processedMovies = 0;
        for (JsonNode movie : hotListResult.payload().path("movieList")) {
            if (processedMovies++ >= movieDetailLimit) {
                break;
            }
                if (!movie.path("id").canConvertToLong()) {
                    rejectedItemCount++;
                    continue;
                }
                ContentQuery query = new ContentQuery(com.miaoyu.ticket.content.domain.ContentResourceType.MOVIE,
                        movie.path("id").longValue(), null, null);
                RawFetchResult detail = fetchWithPolicy(query);
                if (detail.payload() == null) {
                    rejectedItemCount++;
                    failureOutcome = detail.outcome();
                    failureCode = detail.errorCode();
                    continue;
                }
                Optional<ContentResult<List<? extends ContentItem>>> normalized = normalizeAll(query, detail.payload());
                if (normalized.isEmpty()) { rejectedItemCount++; }
                else { synchronizedContent.add(new SynchronizedContent(query, normalized.get())); }
        }
        // Provider 和公开结果只使用行政区划代码；HTTP 适配器负责转换 NetStart 的 ci。
        ContentQuery cinemas = new ContentQuery(com.miaoyu.ticket.content.domain.ContentResourceType.CINEMA,
                null, CHANGSHA_CITY_CODE, "影");
        RawFetchResult cinema = fetchWithPolicy(cinemas);
        if (cinema.payload() == null) {
            rejectedItemCount++;
            failureOutcome = cinema.outcome();
            failureCode = cinema.errorCode();
        } else {
            Optional<ContentResult<List<? extends ContentItem>>> normalized = normalizeAll(cinemas, cinema.payload());
            if (normalized.isEmpty()) { rejectedItemCount++; }
            else { synchronizedContent.add(new SynchronizedContent(cinemas, normalized.get())); }
        }
        int acceptedItemCount = synchronizedContent.stream().mapToInt(content -> content.result().data().size()).sum();
        int attemptedCount = acceptedItemCount + rejectedItemCount;
        // 成功与否必须和内容项统计使用同一口径；一条影院查询可返回多家影院，不能按查询条数判断。
        Outcome outcome = failureOutcome != null ? failureOutcome
                : rejectedItemCount == 0 ? Outcome.SUCCESS : Outcome.FIELD_REJECTED;
        return new DailySyncBatch(synchronizedContent, attemptedCount, outcome, failureCode);
    }

    @Override
    public DailySyncBatch fetchCityCinemas(String cityCode) {
        if (!properties.enabled() || !isLearningEnvironment()) {
            return new DailySyncBatch(List.of(), 0, Outcome.PROVIDER_DISABLED, null);
        }
        ContentQuery query = new ContentQuery(
                com.miaoyu.ticket.content.domain.ContentResourceType.CINEMA, null, cityCode, "影院");
        RawFetchResult fetched = fetchWithPolicy(query);
        if (fetched.payload() == null) {
            return new DailySyncBatch(List.of(), 1, fetched.outcome(), fetched.errorCode());
        }
        Optional<ContentResult<List<? extends ContentItem>>> normalized = normalizeAll(query, fetched.payload());
        if (normalized.isEmpty()) {
            return new DailySyncBatch(List.of(), 1, Outcome.FIELD_REJECTED, null);
        }
        int count = normalized.get().data().size();
        return new DailySyncBatch(List.of(new SynchronizedContent(query, normalized.get())), count,
                Outcome.SUCCESS, null);
    }

    /**
     * 当前热映目录的可恢复影片批次。
     *
     * <p>目录请求占用一次额度，其余九次最多请求九部尚未落库的详情。已完成身份由 MySQL 提供，
     * 任务中断后再次读取目录并过滤即可继续；不保存原始目录、Provider URL 或用户请求参数。</p>
     */
    @Override
    public DailySyncBatch fetchCurrentHotMovies(Set<String> completedSourceMovieIds) {
        java.util.Map<String, ContentPersistencePort.MovieState> states = new java.util.HashMap<>();
        completedSourceMovieIds.forEach(id -> states.put(id, null));
        return fetchCurrentHotMoviesIncremental(states, false);
    }

    @Override
    public DailySyncBatch fetchCurrentHotMovies(
            java.util.Map<String, ContentPersistencePort.MovieState> completedStates) {
        return fetchCurrentHotMoviesIncremental(completedStates, true);
    }

    /**
     * 按已保存的上映资料判断是否跳过详情请求；Provider 没有提供比较字段时不跳过，保证资料变化有机会被更新。
     */
    private DailySyncBatch fetchCurrentHotMoviesIncremental(
            java.util.Map<String, ContentPersistencePort.MovieState> completedStates, boolean compareState) {
        if (!properties.enabled() || !isLearningEnvironment()) {
            return new DailySyncBatch(List.of(), 0, Outcome.PROVIDER_DISABLED, null);
        }
        RawFetchResult directory = fetchWithPolicy(new ContentQuery(
                com.miaoyu.ticket.content.domain.ContentResourceType.MOVIE, null, null, null));
        if (directory.payload() == null) {
            return new DailySyncBatch(List.of(), 1, directory.outcome(), directory.errorCode());
        }
        java.util.ArrayList<SynchronizedContent> accepted = new java.util.ArrayList<>();
        int rejected = 0;
        Outcome failureOutcome = null;
        Integer failureCode = null;
        // 目录已消耗一次请求；不在本轮等待配额恢复，避免管理员请求或调度线程长时间占用。
        int detailBudget = Math.max(0, properties.requestsPerMinute() - 1);
        int requested = 0;
        for (JsonNode movie : directory.payload().path("movieList")) {
            if (!movie.path("id").canConvertToLong()) {
                rejected++;
                continue;
            }
            String externalId = Long.toString(movie.path("id").longValue());
            ContentPersistencePort.MovieState savedState = completedStates.get(externalId);
            if ((!compareState && completedStates.containsKey(externalId))
                    || (compareState && savedState != null && matchesSavedReleaseState(movie, savedState))) {
                continue;
            }
            if (requested++ >= detailBudget) {
                break;
            }
            ContentQuery detailQuery = new ContentQuery(
                    com.miaoyu.ticket.content.domain.ContentResourceType.MOVIE,
                    movie.path("id").longValue(), null, null);
            RawFetchResult detail = fetchWithPolicy(detailQuery);
            if (detail.payload() == null) {
                rejected++;
                failureOutcome = detail.outcome();
                failureCode = detail.errorCode();
                continue;
            }
            Optional<ContentResult<List<? extends ContentItem>>> normalized =
                    normalizeAll(detailQuery, detail.payload());
            if (normalized.isEmpty()) {
                rejected++;
            } else {
                accepted.add(new SynchronizedContent(detailQuery, normalized.get()));
            }
        }
        int acceptedCount = accepted.stream().mapToInt(content -> content.result().data().size()).sum();
        int attempted = acceptedCount + rejected;
        Outcome outcome = failureOutcome != null ? failureOutcome
                : rejected == 0 ? Outcome.SUCCESS : Outcome.FIELD_REJECTED;
        return new DailySyncBatch(accepted, attempted, outcome, failureCode);
    }

    private boolean matchesSavedReleaseState(JsonNode movie, ContentPersistencePort.MovieState savedState) {
        String releaseDate = movie.path("rt").asText(null);
        String releaseStatus = movie.path("globalReleased").asBoolean(false) ? "NOW_SHOWING"
                : releaseDate == null || releaseDate.isBlank() ? null : "COMING_SOON";
        if (!java.util.Objects.equals(releaseDate, savedState.releaseDate())
                || !java.util.Objects.equals(releaseStatus, savedState.releaseStatus())) {
            return false;
        }
        // 目录只提供上映状态和日期；两者都未变化时，已保存影片直接跳过详情请求，避免定时任务重复写入。
        return true;
    }

    /**
     * 所有原始请求统一在此处消耗本地额度，并且只对连接失败或 5xx 重试一次。
     *
     * <p>请求成功只表示收到 JSON，字段质量仍由 normalizeAll 决定；不能因 HTTP 200
     * 就把不完整数据标记为 LIVE 内容。</p>
     *
     * <p>重试前再次检查额度，防止单个异常请求绕开限流。线程被中断时恢复中断标记，
     * 让调度器可按正常停止流程处理，而不是吞掉关闭信号。</p>
     *
     * <p>该方法不会记录 URL、查询参数、Key 或完整响应；调用结果只向上层返回固定分类。
     * 需要排查字段质量时，使用脱敏的数量和 dataTime，而不是扩大第三方内容的保存范围。</p>
     */
    private RawFetchResult fetchWithPolicy(ContentQuery query) {
        if (!allowRequest()) {
            return new RawFetchResult(null, Outcome.RATE_LIMITED, 429);
        }
        try {
            return new RawFetchResult(rawClient.fetch(query), Outcome.SUCCESS, null);
        } catch (ResourceAccessException | RestClientResponseException exception) {
            if (!isRetryable(exception) || !allowRequest()) {
                return failureOf(exception);
            }
            try {
                Thread.sleep(properties.retryBackoff());
                return new RawFetchResult(rawClient.fetch(query), Outcome.SUCCESS, null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new RawFetchResult(null, Outcome.CONNECTION_FAILED, null);
            } catch (ResourceAccessException | RestClientResponseException retryFailure) {
                return failureOf(retryFailure);
            }
        }
    }

    private RawFetchResult failureOf(Exception exception) {
        if (exception instanceof RestClientResponseException response) {
            return new RawFetchResult(null, response.getStatusCode().value() == 429 ? Outcome.RATE_LIMITED
                    : Outcome.UPSTREAM_FAILED, response.getStatusCode().value());
        }
        return new RawFetchResult(null, Outcome.CONNECTION_FAILED, null);
    }

    private record RawFetchResult(JsonNode payload, Outcome outcome, Integer errorCode) { }
}
