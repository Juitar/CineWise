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
 */
@Service
public class ExternalShowtimeQueryService implements ExternalShowtimeQueryPort {

    static final String PROVIDER = "NETSTART_MAOYAN";
    private static final int MAX_CINEMAS = 100;
    private static final int MAX_FUTURE_DAYS = 7;
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
    public QueryResult query(Query query) {
        validate(query);
        if (query.cinemaIds().isEmpty()) {
            return new QueryResult(List.of(), false, null);
        }
        List<Long> cinemaIds = distinctCinemaIds(query.cinemaIds());
        Map<String, ContentExternalIdentityLookupPort.ExternalIdentity> localCinemaIds =
                resolveExternalCinemas(cinemaIds);
        if (localCinemaIds.isEmpty()) {
            // 没有 ACTIVE 外部影院映射是正常的本地资料缺失，不应该访问 Provider 或报成上游故障。
            return new QueryResult(List.of(), false, null);
        }
        List<ExternalShowtimeProvider.ExternalCinema> externalCinemas = localCinemaIds.values().stream()
                .filter(identity -> identity.providerCityId() != null && !identity.providerCityId().isBlank())
                .map(identity -> new ExternalShowtimeProvider.ExternalCinema(
                        identity.externalId(), identity.providerCityId()))
                .toList();
        if (externalCinemas.isEmpty()) {
            return new QueryResult(List.of(), false, null);
        }
        ExternalShowtimeProvider.FetchResult fetched = provider.fetch(query.showDate(), externalCinemas);
        if (!fetched.available()) {
            return fallback(query.showDate(), cinemaIds);
        }
        OffsetDateTime dataAt = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        List<ExternalShowtimeSnapshot> accepted = mapAccepted(fetched.candidates(), localCinemaIds, dataAt);
        OffsetDateTime expiresAt = accepted.stream().map(ExternalShowtimeSnapshot::expiresAt)
                .min(OffsetDateTime::compareTo).orElse(dataAt.plusMinutes(10));
        snapshotPort.save(query.showDate(), cinemaIds,
                new ExternalShowtimeSnapshotPort.Snapshot(accepted, dataAt, expiresAt));
        return new QueryResult(accepted, false, null);
    }

    private QueryResult fallback(LocalDate showDate, List<Long> cinemaIds) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        return snapshotPort.find(showDate, cinemaIds)
                .filter(snapshot -> snapshot.expiresAt().isAfter(now))
                .map(snapshot -> new QueryResult(snapshot.snapshots(), true, FallbackType.SNAPSHOT))
                .orElseThrow(() -> new BusinessException(ShowtimeErrorCode.PROVIDER_UNAVAILABLE));
    }

    private List<ExternalShowtimeSnapshot> mapAccepted(List<ExternalShowtimeProvider.Candidate> candidates,
                                                        Map<String, ContentExternalIdentityLookupPort.ExternalIdentity>
                                                                localCinemaIds,
                                                        OffsetDateTime dataAt) {
        List<String> movieExternalIds = candidates.stream().map(ExternalShowtimeProvider.Candidate::externalMovieId)
                .filter(Objects::nonNull).distinct().toList();
        if (movieExternalIds.isEmpty()) {
            return List.of();
        }
        ContentIdentityResolutionPort.ResolutionBatch resolutions = identityResolutionService.resolve(PROVIDER,
                ContentResourceType.MOVIE, movieExternalIds);
        Map<String, Long> movieIds = new LinkedHashMap<>();
        for (ContentIdentityResolutionPort.Resolution resolution : resolutions.results()) {
            if (resolution.status() == ContentIdentityResolutionPort.ResolutionStatus.RESOLVED) {
                movieIds.put(resolution.externalId(), resolution.internalContentId());
            }
        }
        List<ExternalShowtimeSnapshot> accepted = new ArrayList<>();
        for (ExternalShowtimeProvider.Candidate candidate : candidates) {
            Long movieId = movieIds.get(candidate.externalMovieId());
            ContentExternalIdentityLookupPort.ExternalIdentity cinemaIdentity =
                    localCinemaIds.get(candidate.externalCinemaId());
            Long cinemaId = cinemaIdentity == null ? null : cinemaIdentity.contentId();
            if (movieId == null || cinemaId == null || candidate.startTime() == null
                    || !candidate.startTime().isAfter(dataAt)) {
                continue;
            }
            OffsetDateTime expiresAt = min(candidate.startTime(), dataAt.plusMinutes(10));
            accepted.add(new ExternalShowtimeSnapshot(PROVIDER, candidate.externalShowId(), candidate.externalMovieId(),
                    candidate.externalCinemaId(), movieId, cinemaId, candidate.startTime(), null,
                    candidate.listedPrice(), PriceSemantic.REFERENCE_ONLY, dataAt, expiresAt, false));
        }
        return List.copyOf(accepted);
    }

    private Map<String, ContentExternalIdentityLookupPort.ExternalIdentity> resolveExternalCinemas(
            List<Long> cinemaIds) {
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

    private static List<Long> distinctCinemaIds(List<Long> cinemaIds) {
        return List.copyOf(new LinkedHashSet<>(cinemaIds));
    }

    private void validate(Query query) {
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
}
