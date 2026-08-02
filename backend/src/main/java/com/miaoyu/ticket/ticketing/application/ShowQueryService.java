package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 查询票务权威场次，并通过内容模块公开端口补充影院展示摘要。 */
@Service
public class ShowQueryService {

    private static final int DEMO_WINDOW_DAYS = 7;

    private final ShowQueryRepository repository;
    private final ContentSummaryQueryPort contentSummaryQueryPort;
    private final Clock clock;

    public ShowQueryService(
            ShowQueryRepository repository,
            ContentSummaryQueryPort contentSummaryQueryPort,
            Clock clock) {
        this.repository = repository;
        this.contentSummaryQueryPort = contentSummaryQueryPort;
        this.clock = clock;
    }

    /**
     * 查询指定影片、影院在滚动七天窗口内仍可售的场次。
     * 时间区间统一使用左闭右开语义，避免相邻日期或时段重复返回边界场次。
     */
    @Transactional(readOnly = true)
    public List<ShowSummaryView> queryShows(ShowQuery query) {
        validateQuery(query);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        LocalDate runDate = LocalDate.now(clock);
        LocalDateTime windowEnd = runDate.plusDays(DEMO_WINDOW_DAYS).atStartOfDay();
        LocalDateTime dateStart = query.date() == null ? null : query.date().atStartOfDay();
        LocalDateTime dateEnd = query.date() == null ? null : query.date().plusDays(1).atStartOfDay();

        List<ShowQueryRepository.ShowSnapshot> snapshots = repository.findSaleableShows(
                new ShowQueryRepository.QueryCriteria(
                        query.movieId(),
                        query.cinemaId(),
                        now,
                        windowEnd,
                        dateStart,
                        dateEnd,
                        query.timeFrom(),
                        query.timeTo()));
        Set<Long> cinemaIds = snapshots.stream()
                .map(ShowQueryRepository.ShowSnapshot::cinemaId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Long, ContentSummaryQueryPort.CinemaSummary> cinemaSummaries =
                contentSummaryQueryPort.findCinemaSummaries(cinemaIds);
        return snapshots.stream()
                .map(snapshot -> toView(snapshot, cinemaSummaries))
                .toList();
    }

    private void validateQuery(ShowQuery query) {
        if (query.movieId() <= 0 || query.cinemaId() <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        if (query.timeFrom() != null && query.timeTo() != null && !query.timeFrom().isBefore(query.timeTo())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "timeFrom必须早于timeTo");
        }
    }

    private ShowSummaryView toView(
            ShowQueryRepository.ShowSnapshot snapshot,
            Map<Long, ContentSummaryQueryPort.CinemaSummary> cinemaSummaries) {
        ContentSummaryQueryPort.CinemaSummary cinema = cinemaSummaries.get(snapshot.cinemaId());
        if (cinema == null) {
            throw new BusinessException(TicketingErrorCode.QUERY_UNAVAILABLE);
        }
        return new ShowSummaryView(
                snapshot.showId(),
                snapshot.movieId(),
                snapshot.cinemaId(),
                cinema.name(),
                snapshot.auditoriumId(),
                snapshot.auditoriumName(),
                snapshot.startTime(),
                snapshot.endTime(),
                snapshot.languageVersion(),
                snapshot.basePrice(),
                snapshot.availableSeatCount(),
                snapshot.status(),
                snapshot.dataType(),
                snapshot.version(),
                snapshot.updatedAt());
    }
}
