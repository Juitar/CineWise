package com.miaoyu.ticket.ticketing.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.ticketing.application.AvailableDateQueryService;
import com.miaoyu.ticket.ticketing.application.AvailableMovieQueryService;
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
import java.util.regex.Pattern;
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

    /** REST 与 Agent 路由共用的业务 ID 规范，禁止前导零造成同一 BIGINT 的多种文本形式。 */
    private static final Pattern CANONICAL_BUSINESS_ID = Pattern.compile("[1-9]\\d*");

    private final AvailableDateQueryService availableDateQueryService;
    private final AvailableMovieQueryService availableMovieQueryService;
    private final ShowQueryService showQueryService;
    private final SeatQueryService seatQueryService;

    public ShowController(
            AvailableDateQueryService availableDateQueryService,
            AvailableMovieQueryService availableMovieQueryService,
            ShowQueryService showQueryService,
            SeatQueryService seatQueryService) {
        this.availableDateQueryService = availableDateQueryService;
        this.availableMovieQueryService = availableMovieQueryService;
        this.showQueryService = showQueryService;
        this.seatQueryService = seatQueryService;
    }

    /** 影院详情页一次取得有排期的影片，避免前端下载全量影片后逐项探测场次。 */
    @GetMapping("/available-movies")
    @Operation(summary = "查询指定影院未来七天的可售影片")
    public Result<AvailableMoviesResponse> queryAvailableMovies(@RequestParam String cinemaId) {
        List<AvailableMoviesResponse.AvailableMovieItemResponse> movies = availableMovieQueryService
                .queryAvailableMovies(parseBusinessId(cinemaId))
                .stream()
                .map(view -> new AvailableMoviesResponse.AvailableMovieItemResponse(
                        Long.toString(view.movieId()),
                        view.title(),
                        view.posterUrl(),
                        view.showCount(),
                        toOffsetDateTime(view.nearestStartTime()),
                        view.contentSource(),
                        toOffsetDateTime(view.contentDataTime()),
                        view.scheduleSource(),
                        toOffsetDateTime(view.scheduleDataTime())))
                .toList();
        return Result.success(new AvailableMoviesResponse(movies));
    }

    /**
     * 日期摘要只用于驱动场次页筛选；调用方仍需按选中日期重新读取具体场次。
     * 两个路由ID均在边界解析，避免非法字符串进入聚合SQL或形成无界查询。
     */
    @GetMapping("/available-dates")
    @Operation(summary = "查询固定影片和影院未来七天的可售日期")
    public Result<AvailableDatesResponse> queryAvailableDates(
            @RequestParam String movieId,
            @RequestParam String cinemaId) {
        List<AvailableDatesResponse.AvailableDateItemResponse> dates = availableDateQueryService
                .queryAvailableDates(parseBusinessId(movieId), parseBusinessId(cinemaId))
                .stream()
                .map(view -> new AvailableDatesResponse.AvailableDateItemResponse(
                        view.date(),
                        view.showCount()))
                .toList();
        return Result.success(new AvailableDatesResponse(dates));
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

    /**
     * 在 REST 边界统一收紧业务 ID。
     *
     * Long.parseLong 会接受前导零；如果不先校验，`01` 与 `1` 会指向同一场次，
     * 从而破坏 B 的 Agent 卡片、C 的前端路由和 A 的公开响应之间的一致 ID 表示。
     */
    private long parseBusinessId(String value) {
        if (value == null || !CANONICAL_BUSINESS_ID.matcher(value).matches()) {
            throw invalidBusinessId();
        }
        try {
            long id = Long.parseLong(value);
            return id;
        } catch (NumberFormatException exception) {
            throw invalidBusinessId();
        }
    }

    private BusinessException invalidBusinessId() {
        return new BusinessException(CommonErrorCode.INVALID_PARAMETER, "业务ID必须是无前导零的正整数");
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
