package com.miaoyu.ticket.ticketing.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.ticketing.application.SeatMapView;
import com.miaoyu.ticket.ticketing.application.SeatQueryService;
import com.miaoyu.ticket.ticketing.application.ShowQuery;
import com.miaoyu.ticket.ticketing.application.ShowQueryService;
import com.miaoyu.ticket.ticketing.application.ShowSummaryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 场次公开查询与登录后座位查询的 REST 适配层。 */
@RestController
@RequestMapping("/api/v1/shows")
public class ShowController {

    private final ShowQueryService showQueryService;
    private final SeatQueryService seatQueryService;

    public ShowController(ShowQueryService showQueryService, SeatQueryService seatQueryService) {
        this.showQueryService = showQueryService;
        this.seatQueryService = seatQueryService;
    }

    /** 接收字符串业务 ID 和可选时间筛选，响应金额始终序列化为两位小数字符串。 */
    @GetMapping
    @Operation(summary = "查询固定影片和影院的未来可售场次")
    public Result<List<ShowSummaryResponse>> queryShows(
            @RequestParam String movieId,
            @RequestParam String cinemaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime timeFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime timeTo) {
        ShowQuery query = new ShowQuery(
                parseBusinessId(movieId),
                parseBusinessId(cinemaId),
                date,
                timeFrom,
                timeTo);
        return Result.success(showQueryService.queryShows(query).stream()
                .map(this::toResponse)
                .toList());
    }

    /** 查询后端权威座位快照；页面选中状态不能替代后续锁座结果。 */
    @GetMapping("/{showId}/seats")
    @Operation(summary = "登录后查询场次权威座位图")
    @SecurityRequirement(name = "cookieAuth")
    public Result<SeatMapResponse> querySeatMap(
            @Parameter(description = "十进制字符串场次ID") @PathVariable String showId) {
        return Result.success(toResponse(seatQueryService.querySeatMap(parseBusinessId(showId))));
    }

    private long parseBusinessId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException("ID must be positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "业务ID必须是正整数");
        }
    }

    private ShowSummaryResponse toResponse(ShowSummaryView view) {
        return new ShowSummaryResponse(
                Long.toString(view.showId()),
                Long.toString(view.movieId()),
                Long.toString(view.cinemaId()),
                view.cinemaName(),
                Long.toString(view.auditoriumId()),
                view.auditoriumName(),
                toOffsetDateTime(view.startTime()),
                toOffsetDateTime(view.endTime()),
                toOffsetDateTime(view.expiresAt()),
                view.languageVersion(),
                view.basePrice().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                view.availableSeatCount(),
                view.status(),
                view.dataType(),
                view.stateVersion(),
                toOffsetDateTime(view.updatedAt()));
    }

    private SeatMapResponse toResponse(SeatMapView view) {
        List<SeatMapResponse.SeatItemResponse> seats = view.seats().stream()
                .map(seat -> new SeatMapResponse.SeatItemResponse(
                        Long.toString(seat.seatId()),
                        seat.rowNo(),
                        seat.seatNo(),
                        seat.seatLabel(),
                        seat.status(),
                        seat.stateVersion()))
                .toList();
        return new SeatMapResponse(
                Long.toString(view.showId()),
                Long.toString(view.auditoriumId()),
                view.auditoriumName(),
                view.rowCount(),
                view.seatCount(),
                view.availableSeatCount(),
                view.stateVersion(),
                toOffsetDateTime(view.updatedAt()),
                seats);
    }

    private OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }
}
