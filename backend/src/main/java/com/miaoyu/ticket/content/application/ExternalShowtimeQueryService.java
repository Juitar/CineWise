package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.ErrorCode;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 把外部排期转换为 A 可读取的候选快照。
 *
 * <p>本服务只写 D 自己的通用快照表，既不创建 A 的场次，也不把第三方票价和余座误作交易事实。</p>
 * <p>查询先校验本地日期和影院上限，防止无效请求消耗 Provider 限流额度。</p>
 * <p>本地影院身份必须是 ACTIVE 映射，不能按名称、地址、坐标或城市文本猜测。</p>
 * <p>影片身份同样使用稳定外部 ID，未找到或有歧义的影片只隔离当前候选。</p>
 * <p>Provider 成功时，候选先通过时间、ID 和价格字段校验，再写入快照。</p>
 * <p>Provider 失败时，只读取未过期的最近成功快照，且每条结果标记 SNAPSHOT。</p>
 * <p>过期快照不会因为读取请求而延长 expiresAt，也不会伪装成实时来源。</p>
 * <p>快照保存发生在 Provider 调用之后，避免外部网络耗时持有数据库事务。</p>
 * <p>外部 listedPrice 永远是参考价，A 导入时必须使用自己的沙箱价格。</p>
 * <p>外部余座、座位图、订单和支付信息不在 Provider 候选模型内。</p>
 * <p>每条候选都有三元外部幂等键，避免把影院范围内的 seqNo 当成全局 ID。</p>
 * <p>重复三元键只保留第一次合格记录，防止同一场次重复进入 A 的导入批次。</p>
 * <p>返回超过 200 条时设置 truncated，调用方不能把截断列表当作完整数据集。</p>
 * <p>时间统一使用 Asia/Shanghai 的 OffsetDateTime，避免 JVM 默认时区影响判断。</p>
 * <p>开场时间早于或等于采集时间的记录不会被公开。</p>
 * <p>没有任何合格候选是正常空结果，但 Provider 故障且无快照必须返回 303004。</p>
 * <p>Application DTO 不暴露 Provider 城市编号、ci、URL、Key 或原始响应。</p>
 * <p>身份映射错误沿用 303005、303006、303007，不用一个笼统成功空数组掩盖。</p>
 * <p>降级只改变本次返回标记，不回写快照中的实时采集事实。</p>
 * <p>该服务不访问 A 的 Mapper、Repository、Entity 或票务表。</p>
 * <p>Query 只接受当天至未来七天、最多 100 家影院。</p>
 * <p>QueryResult 最多返回 200 条并标记 truncated。</p>
 * <p>本地影院和影片都必须通过 ACTIVE 外部身份映射。</p>
 * <p>任何缺少稳定 ID 的候选都会被隔离。</p>
 * <p>时间统一为 Asia/Shanghai 的 OffsetDateTime。</p>
 * <p>开场时间不晚于采集时间的候选不会公开。</p>
 * <p>endTime 为空但 durationMinutes 为正时标记 SANDBOX_REFERENCE，供 A 生成本地预计结束时间；D 不自行推算外部散场。</p>
 * <p>A 只有在 endTime 大于 startTime 且状态为 ACCEPTED 时才导入真实本地交易场次。</p>
 * <p>listedPrice 永远只是参考价。</p>
 * <p>A 的本地价格不会被外部价格覆盖。</p>
 * <p>每条候选使用 provider、影院 ID 和场次 ID 三元键。</p>
 * <p>重复三元键只保留第一条。</p>
 * <p>Provider 成功后才保存新快照。</p>
 * <p>Provider 失败不清理旧快照。</p>
 * <p>过期快照不会作为降级结果返回。</p>
 * <p>降级结果逐条标记 SNAPSHOT。</p>
 * <p>没有快照时返回 303004。</p>
 * <p>身份错误沿用 303005、303006、303007。</p>
 * <p>无效参数返回 100001。</p>
 * <p>公开结果不包含 ci 或 providerCityId。</p>
 * <p>公开结果不包含 Provider URL 和原始响应。</p>
 * <p>不访问 A 的票务持久化层。</p>
 * <p>Provider 失败时不会伪造空排期。</p>
 * <p>实时结果和快照结果明确区分。</p>
 * <p>快照降级不会延长采集时间。</p>
 * <p>结果排序保持 Provider 首次出现顺序。</p>
 * <p>输入影院 ID 在 Application 入口去重。</p>
 * <p>身份查询结果按外部 ID 建立索引。</p>
 * <p>解析不到本地电影 ID 的候选直接跳过。</p>
 * <p>解析不到本地影院 ID 的候选直接跳过。</p>
 * <p>无效开始时间不会进入快照。</p>
 * <p>快照过期检查使用注入的 Clock。</p>
 * <p>测试可以固定 Clock 验证边界。</p>
 * <p>错误对象使用固定业务码。</p>
 * <p>错误消息不包含第三方原文。</p>
 * <p>持久化端口只接收不可变候选列表。</p>
 * <p>序列化由基础设施适配器负责。</p>
 * <p>Application 不依赖 JDBC 类型。</p>
 * <p>Application 不依赖 HTTP 客户端。</p>
 * <p>Application 不接触 Provider 原始 JsonNode。</p>
 * <p>排期候选不是可售库存。</p>
 */
@Service
public class ExternalShowtimeQueryService implements ExternalShowtimeQueryPort {

    static final String PROVIDER = "NETSTART_MAOYAN";
    private static final int MAX_CINEMAS = 100;
    private static final int MAX_FUTURE_DAYS = 7;
    private static final int MAX_RESULTS = 200;
    private static final int MAX_REJECTED_RESULTS = 200;
    private final ExternalShowtimeProvider provider;
    private final ContentExternalIdentityLookupPort externalIdentityLookupPort;
    private final ContentIdentityResolutionService identityResolutionService;
    private final ExternalShowtimeSnapshotPort snapshotPort;
    private final Clock clock;

    public ExternalShowtimeQueryService(ExternalShowtimeProvider provider,
                                        ContentExternalIdentityLookupPort externalIdentityLookupPort,
                                        ContentIdentityResolutionService identityResolutionService,
                                        ExternalShowtimeSnapshotPort snapshotPort, Clock clock) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.externalIdentityLookupPort = Objects.requireNonNull(externalIdentityLookupPort,
                "externalIdentityLookupPort must not be null");
        this.identityResolutionService = Objects.requireNonNull(identityResolutionService,
                "identityResolutionService must not be null");
        this.snapshotPort = Objects.requireNonNull(snapshotPort, "snapshotPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    /**
     * 查询实时候选并在成功后更新 D 自己的快照。
     *
     * <p>外部调用始终发生在内容持久化事务之外；任何候选都不会写入 A 的场次、影厅、座位或订单表。</p>
     */
    public QueryResult query(Query query) {
        validate(query);
        if (query.cinemaIds().isEmpty()) {
            return new QueryResult(List.of(), List.of(), false);
        }
        List<Long> cinemaIds = distinctCinemaIds(query.cinemaIds());
        Map<String, ContentExternalIdentityLookupPort.ExternalIdentity> localCinemaIds =
                resolveExternalCinemas(cinemaIds);
        if (localCinemaIds.isEmpty()) {
            // 没有 ACTIVE 外部影院映射是正常的本地资料缺失，不应该访问 Provider 或报成上游故障。
            return new QueryResult(List.of(), List.of(), false);
        }
        List<ExternalShowtimeProvider.ExternalCinema> externalCinemas = localCinemaIds.values().stream()
                .filter(identity -> identity.providerCityId() != null && !identity.providerCityId().isBlank())
                .map(identity -> new ExternalShowtimeProvider.ExternalCinema(
                        identity.externalId(), identity.providerCityId()))
                .toList();
        if (externalCinemas.isEmpty()) {
            return new QueryResult(List.of(), List.of(), false);
        }
        ExternalShowtimeProvider.FetchResult fetched = provider.fetch(query.showDate(), externalCinemas);
        if (!fetched.available()) {
            return fallback(query.showDate(), cinemaIds);
        }
        OffsetDateTime dataAt = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        CandidateMapping mapped = mapCandidates(fetched.candidates(), localCinemaIds, dataAt);
        OffsetDateTime expiresAt = mapped.accepted().stream().map(ExternalShowtimeSnapshot::expiresAt)
                .min(OffsetDateTime::compareTo).orElse(dataAt.plusMinutes(10));
        snapshotPort.save(query.showDate(), cinemaIds,
                new ExternalShowtimeSnapshotPort.Snapshot(mapped.accepted(), dataAt, expiresAt));
        return limitAndMark(mapped);
    }

    private QueryResult fallback(LocalDate showDate, List<Long> cinemaIds) {
        // 只读尚未过期的整批快照，避免将部分实时结果与旧结果混合交给 A 导入。
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        return snapshotPort.find(showDate, cinemaIds)
                .filter(snapshot -> snapshot.expiresAt().isAfter(now))
                .map(snapshot -> new QueryResult(snapshot.snapshots().stream()
                        .map(this::asSnapshotFallback).toList(), List.of(), false))
                .orElseThrow(() -> new BusinessException(ShowtimeErrorCode.PROVIDER_UNAVAILABLE));
    }

    private CandidateMapping mapCandidates(List<ExternalShowtimeProvider.Candidate> candidates,
                                                        Map<String, ContentExternalIdentityLookupPort.ExternalIdentity>
                                                                localCinemaIds,
                                                        OffsetDateTime dataAt) {
        // 影片身份解析批量执行；单个未映射影片只隔离自己，不能阻塞同批其他已映射候选。
        List<String> movieExternalIds = candidates.stream().map(ExternalShowtimeProvider.Candidate::externalMovieId)
                .filter(Objects::nonNull).distinct().toList();
        if (movieExternalIds.isEmpty()) {
            return new CandidateMapping(List.of(), List.of());
        }
        ContentIdentityResolutionPort.ResolutionBatch resolutions = identityResolutionService.resolve(PROVIDER,
                ContentResourceType.MOVIE, movieExternalIds);
        List<String> cinemaExternalIds = candidates.stream()
                .map(ExternalShowtimeProvider.Candidate::externalCinemaId)
                .filter(Objects::nonNull).distinct().toList();
        ContentIdentityResolutionPort.ResolutionBatch cinemaResolutions = identityResolutionService.resolve(PROVIDER,
                ContentResourceType.CINEMA, cinemaExternalIds);
        Map<String, ContentIdentityResolutionPort.Resolution> movieResolutions = new LinkedHashMap<>();
        for (ContentIdentityResolutionPort.Resolution resolution : resolutions.results()) {
            movieResolutions.put(resolution.externalId(), resolution);
        }
        Map<String, ContentIdentityResolutionPort.Resolution> cinemaResolutionByExternalId = new LinkedHashMap<>();
        for (ContentIdentityResolutionPort.Resolution resolution : cinemaResolutions.results()) {
            cinemaResolutionByExternalId.put(resolution.externalId(), resolution);
        }
        Map<ExternalShowtimeKey, ExternalShowtimeSnapshot> accepted = new LinkedHashMap<>();
        List<ExternalShowtimeSnapshot> rejected = new ArrayList<>();
        for (ExternalShowtimeProvider.Candidate candidate : candidates) {
            ContentIdentityResolutionPort.Resolution movieResolution =
                    movieResolutions.get(candidate.externalMovieId());
            Long movieId = movieResolution != null && movieResolution.status()
                    == ContentIdentityResolutionPort.ResolutionStatus.RESOLVED
                    ? movieResolution.internalContentId() : null;
            ContentIdentityResolutionPort.Resolution cinemaResolution =
                    cinemaResolutionByExternalId.get(candidate.externalCinemaId());
            Long cinemaId = cinemaResolution != null && cinemaResolution.status()
                    == ContentIdentityResolutionPort.ResolutionStatus.RESOLVED
                    ? cinemaResolution.internalContentId() : null;
            ExternalShowtimeKey key = new ExternalShowtimeKey(PROVIDER, candidate.externalCinemaId(),
                    candidate.externalShowId());
            if (movieId == null || cinemaId == null) {
                int code = identityErrorCode(movieResolution, cinemaResolution);
                rejected.add(rejected(candidate, movieId, cinemaId, dataAt,
                        QualityStatus.IDENTITY_REJECTED, code, key));
                continue;
            }
            if (candidate.startTime() == null) {
                rejected.add(rejected(candidate, movieId, cinemaId, dataAt,
                        QualityStatus.END_TIME_REJECTED, null, key));
                continue;
            }
            if (!candidate.startTime().isAfter(dataAt)) {
                rejected.add(rejected(candidate, movieId, cinemaId, dataAt, QualityStatus.TIME_REJECTED, null, key));
                continue;
            }
            QualityStatus qualityStatus;
            if (candidate.endTime() != null) {
                if (!candidate.endTime().isAfter(candidate.startTime())) {
                    rejected.add(rejected(candidate, movieId, cinemaId, dataAt,
                            QualityStatus.TIME_REJECTED, null, key));
                    continue;
                }
                qualityStatus = QualityStatus.ACCEPTED;
            } else if (candidate.durationMinutes() != null && candidate.durationMinutes() > 0) {
                // 片长只能支持 A 生成本地预计结束时间，不能冒充 Provider 的真实散场事实。
                qualityStatus = QualityStatus.SANDBOX_REFERENCE;
            } else {
                rejected.add(rejected(candidate, movieId, cinemaId, dataAt,
                        QualityStatus.END_TIME_REJECTED, null, key));
                continue;
            }
            OffsetDateTime expiresAt = min(candidate.startTime(), dataAt.plusMinutes(10));
            ExternalShowtimeSnapshot snapshot = new ExternalShowtimeSnapshot(
                    PROVIDER, candidate.externalShowId(), candidate.externalMovieId(),
                    candidate.externalCinemaId(), movieId, cinemaId, candidate.startTime(), candidate.endTime(),
                    candidate.listedPrice(), candidate.durationMinutes(), candidate.auditoriumText(),
                    PriceSemantic.REFERENCE_ONLY, dataAt, expiresAt, false, false, null,
                    qualityStatus, null, key);
            // seqNo 没有跨影院唯一保证，三元键是 A 后续导入时唯一可复用的幂等身份。
            accepted.putIfAbsent(snapshot.externalShowtimeKey(), snapshot);
        }
        return new CandidateMapping(List.copyOf(accepted.values()), List.copyOf(rejected));
    }

    /**
     * 成功候选与拒绝明细分别限制为 200 条，避免上游批量脏数据把公开 Port 的响应放大。
     * 两个截断标记互不影响：A 只导入成功候选；拒绝明细仅用于定位被隔离的来源数据。
     */
    private QueryResult limitAndMark(CandidateMapping mapped) {
        boolean truncated = mapped.accepted().size() > MAX_RESULTS;
        boolean rejectedTruncated = mapped.rejected().size() > MAX_REJECTED_RESULTS;
        return new QueryResult(mapped.accepted().stream().limit(MAX_RESULTS).toList(),
                mapped.rejected().stream().limit(MAX_REJECTED_RESULTS).toList(), truncated, rejectedTruncated);
    }

    private ExternalShowtimeSnapshot rejected(ExternalShowtimeProvider.Candidate candidate, Long movieId,
                                              Long cinemaId, OffsetDateTime dataAt, QualityStatus qualityStatus,
                                              Integer rejectionCode, ExternalShowtimeKey key) {
        return new ExternalShowtimeSnapshot(PROVIDER, candidate.externalShowId(), candidate.externalMovieId(),
                candidate.externalCinemaId(), movieId, cinemaId, candidate.startTime(), candidate.endTime(),
                candidate.listedPrice(), candidate.durationMinutes(), candidate.auditoriumText(),
                PriceSemantic.REFERENCE_ONLY, dataAt, dataAt, false, false, null,
                qualityStatus, rejectionCode, key);
    }

    /** 影片和影院分别解析；优先返回实际失败身份的稳定错误码，绝不借用另一类资源的结果。 */
    private static int identityErrorCode(ContentIdentityResolutionPort.Resolution movieResolution,
                                         ContentIdentityResolutionPort.Resolution cinemaResolution) {
        if (movieResolution == null || movieResolution.status()
                != ContentIdentityResolutionPort.ResolutionStatus.RESOLVED) {
            return movieResolution == null ? 303005 : movieResolution.errorCode();
        }
        return cinemaResolution == null ? 303005 : cinemaResolution.errorCode();
    }

    private Map<String, ContentExternalIdentityLookupPort.ExternalIdentity> resolveExternalCinemas(
            List<Long> cinemaIds) {
        // 本地影院没有 ACTIVE 映射时不能请求第三方，防止凭显示名称猜错影院。
        Map<String, ContentExternalIdentityLookupPort.ExternalIdentity> externalToLocal = new LinkedHashMap<>();
        for (ContentExternalIdentityLookupPort.ExternalIdentity identity : externalIdentityLookupPort
                .findActiveExternalIds(PROVIDER, ContentResourceType.CINEMA, cinemaIds)) {
            externalToLocal.put(identity.externalId(), identity);
        }
        return externalToLocal;
    }

    private static OffsetDateTime min(OffsetDateTime first, OffsetDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    /** 快照原始内容保留采集事实；仅在本次读路径上标记为降级，不能回写并污染下一次实时结果。 */
    private ExternalShowtimeSnapshot asSnapshotFallback(ExternalShowtimeSnapshot snapshot) {
        return new ExternalShowtimeSnapshot(snapshot.source(), snapshot.externalShowId(), snapshot.externalMovieId(),
                snapshot.externalCinemaId(), snapshot.movieId(), snapshot.cinemaId(), snapshot.startTime(),
                snapshot.endTime(), snapshot.listedPrice(), snapshot.durationMinutes(), snapshot.auditoriumText(),
                snapshot.priceSemantic(), snapshot.dataAt(),
                snapshot.expiresAt(), snapshot.isExpired(), true, FallbackType.SNAPSHOT, snapshot.qualityStatus(),
                snapshot.rejectionCode(), snapshot.externalShowtimeKey());
    }

    private static List<Long> distinctCinemaIds(List<Long> cinemaIds) {
        return List.copyOf(new LinkedHashSet<>(cinemaIds));
    }

    private void validate(Query query) {
        // 参数错误在访问 Provider 前返回，避免无效请求消耗共享限流预算。
        if (query == null || query.showDate() == null || query.cinemaIds() == null
                || query.cinemaIds().size() > MAX_CINEMAS
                || query.cinemaIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ShowtimeErrorCode.INVALID_PARAMETER);
        }
        LocalDate today = LocalDate.now(clock.withZone(ClockConfiguration.BUSINESS_ZONE_ID));
        if (query.showDate().isBefore(today) || query.showDate().isAfter(today.plusDays(MAX_FUTURE_DAYS))) {
            throw new BusinessException(ShowtimeErrorCode.INVALID_PARAMETER);
        }
    }

    enum ShowtimeErrorCode implements ErrorCode {
        INVALID_PARAMETER(100001, "排期候选查询参数不合法", HttpStatus.BAD_REQUEST),
        PROVIDER_UNAVAILABLE(303004, "排期候选暂不可用", HttpStatus.SERVICE_UNAVAILABLE);

        private final int code;
        private final String message;
        private final HttpStatus httpStatus;

        ShowtimeErrorCode(int code, String message, HttpStatus httpStatus) {
            this.code = code;
            this.message = message;
            this.httpStatus = httpStatus;
        }

        @Override public int code() { return code; }
        @Override public String message() { return message; }
        @Override public HttpStatus httpStatus() { return httpStatus; }
    }

    private record CandidateMapping(List<ExternalShowtimeSnapshot> accepted,
                                    List<ExternalShowtimeSnapshot> rejected) { }
}
