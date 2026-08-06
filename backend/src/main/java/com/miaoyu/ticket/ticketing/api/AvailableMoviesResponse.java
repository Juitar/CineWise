package com.miaoyu.ticket.ticketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/** 影院详情页的可售影片响应；价格和余座必须到后续票务页面重新查询。 */
public record AvailableMoviesResponse(List<AvailableMovieItemResponse> movies) {

    public AvailableMoviesResponse {
        movies = List.copyOf(movies);
    }

    /** 海报允许为空，页面使用本地占位，不请求不可信地址。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AvailableMovieItemResponse(
            String movieId,
            String title,
            String posterUrl,
            int showCount,
            OffsetDateTime nearestStartTime,
            String contentSource,
            OffsetDateTime contentDataTime,
            String scheduleSource,
            OffsetDateTime scheduleDataTime) {
    }
}
