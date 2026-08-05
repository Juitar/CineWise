package com.miaoyu.ticket.order.api;

import java.util.List;

/** Agent 确认前预检的最小输入；不包含 actionId、用户、金额或幂等键。 */
public record CreateOrderPrecheckCommand(String showId, List<String> seatIds) {

    /** 在 A 的 API 边界将十进制字符串转换为内部 long，非法输入由调用方映射为安全错误码。 */
    public long parsedShowId() {
        return parsePositiveId(showId, "showId");
    }

    /** 转换座位 ID；数量、重复和正数规则在同一边界一次完成。 */
    public List<Long> parsedSeatIds() {
        if (seatIds == null || seatIds.isEmpty() || seatIds.size() > 6
                || seatIds.stream().anyMatch(seatId -> seatId == null || seatId.isBlank())) {
            throw new IllegalArgumentException("seatIds 数量或内容不合法");
        }
        List<Long> parsed = seatIds.stream().map(seatId -> parsePositiveId(seatId, "seatId")).toList();
        if (parsed.stream().distinct().count() != parsed.size()) {
            throw new IllegalArgumentException("seatIds 不能重复");
        }
        return parsed;
    }

    private long parsePositiveId(String value, String fieldName) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(fieldName + "必须是正十进制字符串");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + "超出BIGINT范围", exception);
        }
    }
}
