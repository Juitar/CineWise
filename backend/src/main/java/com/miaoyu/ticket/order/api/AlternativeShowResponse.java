package com.miaoyu.ticket.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 替代场次只返回A拥有的排期、票价和实时余座事实。
 *
 * <ul>
 *   <li>ID使用字符串，避免JavaScript精度损失；</li>
 *   <li>票价使用两位小数字符串；</li>
 *   <li>开场时间携带明确时区偏移；</li>
 *   <li>余座是展示快照，不能代替建单时条件锁座。</li>
 * </ul>
 */
public record AlternativeShowResponse(
        @Schema(example = "70002") String showId,
        @Schema(example = "10001") String movieId,
        @Schema(example = "20001") String cinemaId,
        OffsetDateTime startTime,
        @Schema(example = "39.00") String basePrice,
        @Schema(example = "ON_SALE") String status,
        int availableSeatCount) {
}
