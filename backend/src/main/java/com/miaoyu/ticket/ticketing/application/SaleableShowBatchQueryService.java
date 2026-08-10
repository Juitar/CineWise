package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    public static final int MAX_LIMIT = 200;
    public static final int MAX_CINEMA_IDS = 100;
    /**
     * 推荐卡片必须留出用户阅读并进入选座页的时间。最终下单前仍会由票务服务重新校验场次、价格和座位，
     * 这个窗口只约束“这次查询结果”在 Agent 卡片中的可使用时间，并不承诺库存或价格锁定。
     */
    private static final long SNAPSHOT_TTL_SECONDS = 300;
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

        // 所有记录共享同一 dataAt，确保 D 不会因逐条取时产生不一致的候选时效。
        LocalDateTime dataAt = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID)
                .truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime now = dataAt;
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
                            MAX_LIMIT + 1));
        } catch (DataAccessException exception) {
            // 查询故障与“无匹配场次”语义不同，必须让调用方得到稳定的不可用错误。
            throw new BusinessException(TicketingErrorCode.QUERY_UNAVAILABLE);
        }

        boolean truncated = snapshots.size() > MAX_LIMIT;
        int resultSize = Math.min(snapshots.size(), MAX_LIMIT);
        List<SaleableShowView> shows = new ArrayList<>(resultSize);
        for (int index = 0; index < resultSize; index++) {
            shows.add(toView(snapshots.get(index), dataAt));
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
                List.copyOf(uniqueCinemaIds));
    }

    private SaleableShowView toView(ShowQueryRepository.ShowSnapshot snapshot, LocalDateTime dataAt) {
        // 快照最多可被推荐层使用五分钟；更早开场的场次以开场时刻为最终边界。
        LocalDateTime expiresAt = snapshot.startTime().isBefore(dataAt.plusSeconds(SNAPSHOT_TTL_SECONDS))
                ? snapshot.startTime()
                : dataAt.plusSeconds(SNAPSHOT_TTL_SECONDS);
        return new SaleableShowView(
                snapshot.showId(),
                snapshot.movieId(),
                snapshot.cinemaId(),
                snapshot.basePrice().setScale(2, RoundingMode.UNNECESSARY),
                snapshot.startTime(),
                snapshot.endTime(),
                snapshot.dataType(),
                snapshot.source(),
                dataAt,
                expiresAt,
                true,
                snapshot.availableSeatCount(),
                snapshot.version(),
                snapshot.updatedAt());
    }

    private BusinessException invalidParameter() {
        return new BusinessException(CommonErrorCode.INVALID_PARAMETER);
    }

    private record ValidatedQuery(LocalDate date, List<Long> cinemaIds) {
    }
}
