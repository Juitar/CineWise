package com.miaoyu.ticket.ticketing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 可售日期REST响应；顶层包装保留后续日期摘要兼容扩展空间。 */
public record AvailableDatesResponse(List<AvailableDateItemResponse> dates) {

    /** 单日场次数量不是库存，页面仍须按日期重新查询场次并在建单时校验座位。 */
    public record AvailableDateItemResponse(
            @Schema(example = "2026-08-05") LocalDate date,
            @Schema(example = "6", minimum = "0") int showCount) {
    }
}
