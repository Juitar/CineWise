package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.api.PageResult;
import com.miaoyu.ticket.common.config.ApiProperties;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将 A 的可售场次事实与 D 的影院展示摘要组合为影片购票入口数据。 */
@Service
public class AvailableCinemaQueryService {

    private static final int WINDOW_DAYS = 7;

    private final AvailableCinemaQueryRepository repository;
    private final ContentSummaryQueryPort contentSummaryQueryPort;
    private final ApiProperties apiProperties;
    private final Clock clock;

    public AvailableCinemaQueryService(
            AvailableCinemaQueryRepository repository,
            ContentSummaryQueryPort contentSummaryQueryPort,
            ApiProperties apiProperties,
            Clock clock) {
        this.repository = repository;
        this.contentSummaryQueryPort = contentSummaryQueryPort;
        this.apiProperties = apiProperties;
        this.clock = clock;
    }

    /** 合法但没有排期的影片返回空页；内容和票务不可用保持各自稳定错误语义。 */
    @Transactional(readOnly = true)
    public PageResult<AvailableCinemaView> queryAvailableCinemas(String movieId, Integer page, Integer size) {
        long parsedMovieId = parseBusinessId(movieId);
        int resolvedPage = page == null ? apiProperties.defaultPage() : page;
        int resolvedSize = size == null ? apiProperties.defaultPageSize() : size;
        long offset = (long) (resolvedPage - 1) * resolvedSize;
        if (resolvedPage < 1 || resolvedSize < 1 || resolvedSize > apiProperties.maxPageSize()
                || offset > Integer.MAX_VALUE) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        Clock businessClock = clock.withZone(ClockConfiguration.BUSINESS_ZONE_ID);
        LocalDateTime now = LocalDateTime.ofInstant(businessClock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        LocalDateTime end = LocalDate.now(businessClock).plusDays(WINDOW_DAYS).atStartOfDay();
        AvailableCinemaQueryRepository.QueryCriteria criteria =
                new AvailableCinemaQueryRepository.QueryCriteria(parsedMovieId, now, end, (int) offset, resolvedSize);
        try {
            long total = repository.countAvailableCinemas(criteria);
            if (total == 0) {
                return new PageResult<>(0, resolvedPage, resolvedSize, java.util.List.of());
            }
            var snapshots = repository.findAvailableCinemas(criteria);
            Set<Long> cinemaIds = snapshots.stream()
                    .map(AvailableCinemaQueryRepository.AvailableCinemaSnapshot::cinemaId)
                    .collect(Collectors.toUnmodifiableSet());
            Map<Long, ContentSummaryQueryPort.CinemaSummary> summaries = contentSummaryQueryPort
                    .findCinemaSummaries(cinemaIds).cinemas().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            ContentSummaryQueryPort.CinemaSummary::cinemaId, summary -> summary));
            var records = snapshots.stream()
                    .filter(snapshot -> summaries.containsKey(snapshot.cinemaId()))
                    .map(snapshot -> toView(snapshot, summaries.get(snapshot.cinemaId())))
                    .toList();
            return new PageResult<>(total, resolvedPage, resolvedSize, records);
        } catch (DataAccessException exception) {
            // A 的票务事实不可读时不能把系统故障伪装为“没有影院”。
            throw new BusinessException(TicketingErrorCode.QUERY_UNAVAILABLE);
        }
    }

    private long parseBusinessId(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    private AvailableCinemaView toView(
            AvailableCinemaQueryRepository.AvailableCinemaSnapshot snapshot,
            ContentSummaryQueryPort.CinemaSummary summary) {
        return new AvailableCinemaView(snapshot.cinemaId(), summary.name(), summary.address(),
                snapshot.availableShowCount(), snapshot.nearestStartTime(), summary.source(), summary.dataTime(),
                summary.expiresAt(), summary.expired(), snapshot.scheduleSource(), snapshot.scheduleDataTime());
    }
}
