package com.miaoyu.ticket.recommendation.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import com.miaoyu.ticket.recommendation.domain.RankedRecommendationCandidate;
import com.miaoyu.ticket.recommendation.domain.CinemaDistanceSelector;
import com.miaoyu.ticket.recommendation.domain.RecommendationConstraints;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlan;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanRanker;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import com.miaoyu.ticket.recommendation.domain.RecommendationRelaxationAdvisor;
import com.miaoyu.ticket.recommendation.domain.RelaxationSuggestion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** D 的完整推荐查询用例：合并内容资料和 A 的可售场次后生成确定性方案。 */
@Service
public class PersonalizedRecommendationQueryService {

    /*
     * 本类只读 D 的内容 Application Service 与 A 的批量只读端口。
     * 内容资料仅补充类型和评分，绝不生成场次、价格、库存或座位事实。
     * A 的批量结果可能被截断，结果会显式标为 degraded，不能伪装成完整候选集合。
     * 空方案是成功结果；调用方可据此展示放宽建议，但不能自动放宽条件。
     * 所有最终结果在这里统一写入最小指标，指标中不含用户、会话、追踪或定位资料。
     */
    private static final String ALGORITHM_VERSION = "rec-mvp-1";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final RecommendationContentCandidateQueryService cinemaQueryService;
    private final ContentQueryService contentQueryService;
    private final RecommendationBatchShowtimeQueryPort showtimeQueryPort;
    private final RecommendationMetricsRecorder metricsRecorder;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final DistanceContextService distanceContextService;

    @Autowired
    public PersonalizedRecommendationQueryService(
            RecommendationContentCandidateQueryService cinemaQueryService,
            ContentQueryService contentQueryService,
            RecommendationBatchShowtimeQueryPort showtimeQueryPort,
            RecommendationMetricsRecorder metricsRecorder,
            ObjectMapper objectMapper,
            Clock clock) {
        this(cinemaQueryService, contentQueryService, showtimeQueryPort, metricsRecorder, objectMapper, clock, null);
    }

    /** 生产入口额外接入一次性距离上下文；普通推荐仍可使用旧构造器。 */
    public PersonalizedRecommendationQueryService(
            RecommendationContentCandidateQueryService cinemaQueryService,
            ContentQueryService contentQueryService,
            RecommendationBatchShowtimeQueryPort showtimeQueryPort,
            RecommendationMetricsRecorder metricsRecorder,
            ObjectMapper objectMapper,
            Clock clock,
            DistanceContextService distanceContextService) {
        this.cinemaQueryService = cinemaQueryService;
        this.contentQueryService = contentQueryService;
        this.showtimeQueryPort = showtimeQueryPort;
        this.metricsRecorder = metricsRecorder;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.distanceContextService = distanceContextService;
    }

    /**
     * 以当前明确条件计算方案；内容或票务不可用时保留其稳定业务错误，不伪装为空方案。
     *
     * <p>城市影院 ID 在 D 内解析后才交给 A，A 不需要读取 D 的内容持久层；D 同样不会越过 A 的
     * Application API 读取 `movie_show`。</p>
     */
    public RecommendationPlanResult query(RecommendationConstraints constraints) {
        return queryInternal(constraints, null);
    }

    /**
     * B 在可信 ToolContext 中传入本次上下文 ID 和 runId；D 在本方法内消费一次坐标，随后才访问 A。
     * 上下文不存在、过期、归属不符或已消费时返回普通城市推荐，不把失败伪装成“附近没有影院”。
     */
    public RecommendationPlanResult queryWithDistanceContext(
            RecommendationConstraints constraints, String distanceContextId, String runId) {
        if (distanceContextService == null || distanceContextId == null || distanceContextId.isBlank()) {
            return queryInternal(constraints, null);
        }
        CinemaDistanceSelector.Coordinate coordinate = distanceContextService.consume(distanceContextId, runId);
        return queryInternal(constraints, coordinate);
    }

    private RecommendationPlanResult queryInternal(
            RecommendationConstraints constraints, CinemaDistanceSelector.Coordinate coordinate) {
        List<RecommendationContentCandidateQueryService.CinemaCandidate> cinemas = cinemaQueryService
                .listCinemas(constraints.cityCode()).stream()
                .filter(cinema -> !cinema.expired())
                .sorted(Comparator.comparingLong(RecommendationContentCandidateQueryService.CinemaCandidate::cinemaId))
                .limit(100)
                .toList();
        Instant now = clock.instant();
        if (cinemas.isEmpty()) {
            return record(emptyResult("CONTENT", now, now.plusSeconds(60), false, List.of("CONTENT"), null));
        }

        Map<String, Integer> distanceMeters = Map.of();
        if (coordinate != null) {
            List<CinemaDistanceSelector.DistanceCinema> nearest = CinemaDistanceSelector.selectNearest(
                    coordinate,
                    cinemas.stream().map(cinema -> new CinemaDistanceSelector.CoordinateCinema(
                            cinema.cinemaId(), cinema.longitude(), cinema.latitude())).toList(),
                    constraints.maxDistanceMeters());
            distanceMeters = nearest.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    cinema -> Long.toString(cinema.cinemaId()),
                    CinemaDistanceSelector.DistanceCinema::distanceMeters));
            if (distanceMeters.isEmpty()) {
                return queryInternal(constraints, null);
            }
            final Map<String, Integer> selectedDistances = distanceMeters;
            cinemas = cinemas.stream().filter(cinema -> selectedDistances.containsKey(Long.toString(cinema.cinemaId())))
                    .toList();
        }

        RecommendationBatchShowtimeQueryPort.BatchResult batch = showtimeQueryPort.querySaleable(
                constraints.date(), cinemas.stream()
                        .map(RecommendationContentCandidateQueryService.CinemaCandidate::cinemaId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        Map<String, MovieContent> movies = loadMovies();
        Map<String, String> cinemaNames = cinemas.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                cinema -> Long.toString(cinema.cinemaId()),
                RecommendationContentCandidateQueryService.CinemaCandidate::name,
                (left, right) -> left));
        List<RankedRecommendationCandidate> candidates = batch.candidates().stream()
                .map(candidate -> enrich(candidate, movies.get(candidate.movieId()),
                        cinemaNames.get(candidate.cinemaId())))
                .flatMap(java.util.Optional::stream)
                .toList();
        if (candidates.isEmpty()) {
            String source = batch.candidates().isEmpty()
                    ? "TICKETING" : batch.candidates().getFirst().source();
            return record(emptyResult(
                    source,
                    now,
                    now.plusSeconds(60),
                    batch.truncated(),
                    List.of("SHOWTIME"),
                    null));
        }

        List<RecommendationPlan> plans = RecommendationPlanRanker.rank(candidates, constraints, clock);
        if (!distanceMeters.isEmpty()) {
            RecommendationPlan nearest = RecommendationPlanRanker.nearest(
                    candidates, distanceMeters, constraints, clock);
            if (nearest != null) {
                plans = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(nearest),
                        plans.stream().filter(plan -> !plan.showId().equals(nearest.showId()))).toList();
            }
        }
        Instant dataAt = candidates.stream()
                .map(RankedRecommendationCandidate::dataAt)
                .max(Instant::compareTo)
                .orElse(now);
        Instant expiresAt = candidates.stream().map(RankedRecommendationCandidate::expiresAt).min(Instant::compareTo)
                .orElse(now.plusSeconds(60));
        List<String> missingFactors = candidates.stream().anyMatch(candidate -> candidate.rating() == null)
                ? List.of("RATING") : List.of();
        var suggestion = plans.isEmpty()
                ? RecommendationRelaxationAdvisor.suggest(candidates, constraints, clock).orElse(null) : null;
        String source = candidates.getFirst().source();
        return record(new RecommendationPlanResult(ALGORITHM_VERSION, plans, missingFactors, suggestion, source,
                dataAt, expiresAt, batch.truncated() || plans.isEmpty()));
    }

    private RecommendationPlanResult record(RecommendationPlanResult result) {
        // 指标必须在最终结果确定后记录，避免过滤中的中间状态被当作用户实际看到的结果。
        metricsRecorder.record(result);
        return result;
    }

    private Map<String, MovieContent> loadMovies() {
        // 一次读取整个影片目录，再按 A 返回的内部 movieId 合并，避免逐条内容查询扩大超时和缓存压力。
        return contentQueryService.query(new ContentQuery(ContentResourceType.MOVIE, null, null, null))
                .data()
                .stream()
                .map(MovieContent.class::cast)
                .filter(movie -> movie.movieId() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(movie -> Long.toString(movie.movieId()),
                        Function.identity(), (left, right) -> left));
    }

    private java.util.Optional<RankedRecommendationCandidate> enrich(
            RankedRecommendationCandidate showtimeCandidate, MovieContent movie, String cinemaName) {
        if (movie == null) {
            // 没有内容资料时不能把空类型当成“符合类型”，直接排除该可售场次。
            return java.util.Optional.empty();
        }
        try {
            List<String> genres = objectMapper.readValue(movie.genresJson(), new TypeReference<>() { });
            return java.util.Optional.of(new RankedRecommendationCandidate(
                    showtimeCandidate.movieId(), movie.title(), showtimeCandidate.cinemaId(), cinemaName,
                    showtimeCandidate.showId(), showtimeCandidate.price(), showtimeCandidate.startTime(),
                    showtimeCandidate.endTime(), genres, movie.rating(), showtimeCandidate.availableSeatCount(),
                    showtimeCandidate.source(),
                    showtimeCandidate.dataAt(),
                    showtimeCandidate.expiresAt()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // 损坏内容资料不能降级为无类型影片，否则会绕过用户的类型和排除条件。
            return java.util.Optional.empty();
        }
    }

    private static RecommendationPlanResult emptyResult(
            String source, Instant dataAt, Instant expiresAt, boolean degraded,
            List<String> missingFactors,
            RelaxationSuggestion suggestion) {
        return new RecommendationPlanResult(ALGORITHM_VERSION, List.of(), missingFactors, suggestion, source,
                dataAt, expiresAt, degraded);
    }
}
