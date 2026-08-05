package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 为推荐模块提供按日期和多个本地影院查询的票务权威只读入口。 */
@Service
public class SaleableShowBatchQueryService {

    public static final int DEFAULT_LIMIT = 200;
    public static final int MAX_LIMIT = 200;
    public static final int MAX_CINEMA_IDS = 50;
    private static final int ROLLING_WINDOW_DAYS = 7;

    private final ShowQueryRepository repository;
    private final Clock clock;

    public SaleableShowBatchQueryService(ShowQueryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 查询真实可售候选。仓储额外读取一条探测记录，用于在不全量加载的情况下判断是否截断。
     */
    @Transactional(readOnly = true)
    public SaleableShowBatchResult query(SaleableShowBatchQuery query) {
        ValidatedQuery validated = validate(query);
        if (validated.cinemaIds().isEmpty()) {
            return new SaleableShowBatchResult(List.of(), false);
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        LocalDateTime dateStart = validated.date().atStartOfDay();
        LocalDateTime dateEnd = validated.date().plusDays(1).atStartOfDay();
        List<ShowQueryRepository.ShowSnapshot> snapshots;
        try {
            snapshots = repository.findSaleableShowsByCinemaIds(
                    new ShowQueryRepository.BatchQueryCriteria(
                            validated.cinemaIds(),
                            now,
                            dateStart,
                            dateEnd,
                            validated.timeFrom(),
                            validated.timeTo(),
                            validated.limit() + 1));
        } catch (DataAccessException exception) {
            // 查询故障与“无匹配场次”语义不同，必须让调用方得到稳定的不可用错误。
            throw new BusinessException(TicketingErrorCode.QUERY_UNAVAILABLE);
        }

        boolean truncated = snapshots.size() > validated.limit();
        int resultSize = Math.min(snapshots.size(), validated.limit());
        List<SaleableShowView> shows = new ArrayList<>(resultSize);
        for (int index = 0; index < resultSize; index++) {
            shows.add(toView(snapshots.get(index)));
        }
        return new SaleableShowBatchResult(shows, truncated);
    }

    private ValidatedQuery validate(SaleableShowBatchQuery query) {
        if (query == null || query.date() == null || query.cinemaIds() == null) {
            throw invalidParameter();
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        if (query.date().isBefore(today) || !query.date().isBefore(today.plusDays(ROLLING_WINDOW_DAYS))) {
            throw invalidParameter();
        }
        boolean hasTimeFrom = query.timeFrom() != null;
        boolean hasTimeTo = query.timeTo() != null;
        if (hasTimeFrom != hasTimeTo
                || (hasTimeFrom && !query.timeFrom().isBefore(query.timeTo()))) {
            throw invalidParameter();
        }

        int limit = query.limit() == null ? DEFAULT_LIMIT : query.limit();
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw invalidParameter();
        }
        Set<Long> uniqueCinemaIds = new LinkedHashSet<>();
        for (Long cinemaId : query.cinemaIds()) {
            if (cinemaId == null || cinemaId <= 0) {
                throw invalidParameter();
            }
            uniqueCinemaIds.add(cinemaId);
        }
        if (uniqueCinemaIds.size() > MAX_CINEMA_IDS) {
            throw invalidParameter();
        }
        return new ValidatedQuery(
                query.date(),
                List.copyOf(uniqueCinemaIds),
                query.timeFrom(),
                query.timeTo(),
                limit);
    }

    private SaleableShowView toView(ShowQueryRepository.ShowSnapshot snapshot) {
        return new SaleableShowView(
                snapshot.showId(),
                snapshot.movieId(),
                snapshot.cinemaId(),
                snapshot.basePrice().setScale(2, RoundingMode.UNNECESSARY),
                snapshot.startTime(),
                snapshot.endTime(),
                snapshot.startTime(),
                snapshot.dataType(),
                true,
                snapshot.availableSeatCount(),
                snapshot.version(),
                snapshot.updatedAt());
    }

    private BusinessException invalidParameter() {
        return new BusinessException(CommonErrorCode.INVALID_PARAMETER);
    }

    private record ValidatedQuery(
            LocalDate date,
            List<Long> cinemaIds,
            java.time.LocalTime timeFrom,
            java.time.LocalTime timeTo,
            int limit) {
    }
}
