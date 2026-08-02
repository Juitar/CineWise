package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 登录用户查询最新权威座位图的应用服务。 */
@Service
public class SeatQueryService {

    private static final String ON_SALE = "ON_SALE";

    private final ShowQueryRepository repository;
    private final CurrentUserAccessor currentUserAccessor;
    private final Clock clock;

    public SeatQueryService(
            ShowQueryRepository repository,
            CurrentUserAccessor currentUserAccessor,
            Clock clock) {
        this.repository = repository;
        this.currentUserAccessor = currentUserAccessor;
        this.clock = clock;
    }

    /**
     * 返回一个仍可售场次的最新座位快照；身份只从认证上下文获取。
     * 前端选中状态不具有锁座效力，后续建单仍须通过数据库条件更新竞争座位。
     */
    @Transactional(readOnly = true)
    public SeatMapView querySeatMap(long showId) {
        if (showId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        currentUserAccessor.requireCurrentUserId();
        ShowQueryRepository.ShowSeatHeader header = repository.findShowSeatHeader(showId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        if (!ON_SALE.equals(header.status()) || !header.startTime().isAfter(now)) {
            throw new BusinessException(TicketingErrorCode.SHOW_NOT_SALEABLE);
        }
        List<SeatMapView.SeatItemView> seats = repository.findSeats(showId).stream()
                .map(seat -> new SeatMapView.SeatItemView(
                        seat.seatId(),
                        seat.rowNo(),
                        seat.seatNo(),
                        seat.seatLabel(),
                        seat.status(),
                        seat.version()))
                .toList();
        return new SeatMapView(
                header.showId(),
                header.auditoriumId(),
                header.auditoriumName(),
                header.rowCount(),
                header.seatCount(),
                header.availableSeatCount(),
                header.version(),
                header.updatedAt(),
                seats);
    }
}
