package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.content.application.ContentPurchaseQueryPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 按影院聚合未来七天可售影片，并通过 D 的公开端口补齐标题和海报。 */
@Service
public class AvailableMovieQueryService {

    private static final int DEMO_WINDOW_DAYS = 7;

    private final AvailableMovieQueryRepository repository;
    private final ContentPurchaseQueryPort contentPurchaseQueryPort;
    private final Clock clock;

    public AvailableMovieQueryService(
            AvailableMovieQueryRepository repository,
            ContentPurchaseQueryPort contentPurchaseQueryPort,
            Clock clock) {
        this.repository = repository;
        this.contentPurchaseQueryPort = contentPurchaseQueryPort;
        this.clock = clock;
    }

    /**
     * 合法影院没有排期时返回空列表。场次数只用于详情页选择提示，场次页和建单仍重新查询当前状态。
     */
    @Transactional(readOnly = true)
    public List<AvailableMovieView> queryAvailableMovies(long cinemaId) {
        if (cinemaId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        Clock businessClock = clock.withZone(ClockConfiguration.BUSINESS_ZONE_ID);
        LocalDateTime now = LocalDateTime.ofInstant(businessClock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        LocalDateTime windowEnd = LocalDate.now(businessClock).plusDays(DEMO_WINDOW_DAYS).atStartOfDay();
        List<AvailableMovieQueryRepository.AvailableMovieSnapshot> snapshots = repository.findAvailableMovies(
                new AvailableMovieQueryRepository.QueryCriteria(cinemaId, now, windowEnd));
        Set<Long> movieIds = snapshots.stream()
                .map(AvailableMovieQueryRepository.AvailableMovieSnapshot::movieId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Long, ContentPurchaseQueryPort.MovieSummary> summaries =
                contentPurchaseQueryPort.findMovieSummaries(movieIds);
        return snapshots.stream()
                .filter(snapshot -> summaries.containsKey(snapshot.movieId()))
                .map(snapshot -> toView(snapshot, summaries.get(snapshot.movieId())))
                .toList();
    }

    private AvailableMovieView toView(
            AvailableMovieQueryRepository.AvailableMovieSnapshot snapshot,
            ContentPurchaseQueryPort.MovieSummary summary) {
        return new AvailableMovieView(
                snapshot.movieId(),
                summary.title(),
                summary.posterUrl(),
                snapshot.showCount(),
                snapshot.nearestStartTime(),
                snapshot.dataSource(),
                snapshot.dataTime());
    }
}
