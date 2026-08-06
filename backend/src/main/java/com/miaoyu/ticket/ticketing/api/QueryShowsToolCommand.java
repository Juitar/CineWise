package com.miaoyu.ticket.ticketing.api;

import com.miaoyu.ticket.agent.domain.tool.ToolCommand;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/** Agent 查询具体场次的强类型命令；日期与时间已在适配器边界完成解析。 */
public record QueryShowsToolCommand(
        String movieId, String cinemaId, LocalDate businessDate, LocalTime timeFrom, LocalTime timeTo)
        implements ToolCommand {

    public QueryShowsToolCommand {
        QueryAvailableDatesToolCommand.parsePositiveBusinessId(movieId, "movieId");
        QueryAvailableDatesToolCommand.parsePositiveBusinessId(cinemaId, "cinemaId");
        Objects.requireNonNull(businessDate, "businessDate不能为空");
        if (timeFrom != null && timeTo != null && !timeFrom.isBefore(timeTo)) {
            throw new IllegalArgumentException("timeFrom必须早于timeTo");
        }
    }

    public long parsedMovieId() {
        return QueryAvailableDatesToolCommand.parsePositiveBusinessId(movieId, "movieId");
    }

    public long parsedCinemaId() {
        return QueryAvailableDatesToolCommand.parsePositiveBusinessId(cinemaId, "cinemaId");
    }
}
