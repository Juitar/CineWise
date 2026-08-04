package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查询A权威排期中指定影片和影院未来可选的业务日期。
 *
 * <p>合法ID没有排期时返回空集合；内容存在性和展示摘要仍由D的公开能力负责。</p>
 */
@Service
public class AvailableDateQueryService {

    private static final int DEMO_WINDOW_DAYS = 7;

    private final AvailableDateQueryRepository repository;
    private final Clock clock;

    public AvailableDateQueryService(AvailableDateQueryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 使用与场次列表一致的未来七天左闭右开日期窗口。
     *
     * <p>当前时刻使用严格大于条件，因此恰好开场的场次已经失效；showCount只统计排期，
     * 不读取座位表，也不向调用方承诺查询后仍可锁座。</p>
     *
     * <p>即使某场次当前余座为零，日期仍保留；页面可展示售罄场次，最终库存由选座和建单重新校验。</p>
     */
    @Transactional(readOnly = true)
    public List<AvailableDateView> queryAvailableDates(long movieId, long cinemaId) {
        validateBusinessIds(movieId, cinemaId);
        Clock businessClock = clock.withZone(ClockConfiguration.BUSINESS_ZONE_ID);
        LocalDateTime now = LocalDateTime.ofInstant(businessClock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        LocalDateTime windowEnd = LocalDate.now(businessClock)
                .plusDays(DEMO_WINDOW_DAYS)
                .atStartOfDay();

        AvailableDateQueryRepository.QueryCriteria criteria =
                new AvailableDateQueryRepository.QueryCriteria(movieId, cinemaId, now, windowEnd);
        return repository.findAvailableDates(criteria).stream()
                .map(snapshot -> new AvailableDateView(snapshot.date(), snapshot.showCount()))
                .toList();
    }

    /** Application边界重复校验，防止非HTTP消费者绕过Controller形成无界查询。 */
    private void validateBusinessIds(long movieId, long cinemaId) {
        if (movieId <= 0 || cinemaId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }
}
