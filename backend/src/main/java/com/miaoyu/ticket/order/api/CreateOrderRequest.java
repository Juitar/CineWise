package com.miaoyu.ticket.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 建单请求只包含用户选择，不接收用户ID、金额或订单状态。 */
public record CreateOrderRequest(
        @Schema(description = "十进制字符串场次ID", example = "70001")
        @NotBlank
        String showId,
        @Schema(description = "1至6个不重复的十进制字符串座位ID")
        @Size(min = 1, max = 6)
        List<@NotBlank String> seatIds,
        @Schema(description = "一次建单意图内稳定的恢复标识", maxLength = 64)
        @NotBlank
        @Size(max = 64)
        String clientRequestId) {
}
