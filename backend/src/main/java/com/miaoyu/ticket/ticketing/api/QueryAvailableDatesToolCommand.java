package com.miaoyu.ticket.ticketing.api;

import com.miaoyu.ticket.agent.domain.tool.ToolCommand;

/** Agent 查询可选日期的最小输入；业务 ID 保持字符串以避免前端和模型精度损失。 */
public record QueryAvailableDatesToolCommand(String movieId, String cinemaId) implements ToolCommand {

    public QueryAvailableDatesToolCommand {
        validatePositiveBusinessId(movieId, "movieId");
        validatePositiveBusinessId(cinemaId, "cinemaId");
    }

    public long parsedMovieId() {
        return parsePositiveBusinessId(movieId, "movieId");
    }

    public long parsedCinemaId() {
        return parsePositiveBusinessId(cinemaId, "cinemaId");
    }

    static long parsePositiveBusinessId(String value, String fieldName) {
        validatePositiveBusinessId(value, fieldName);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " 超出BIGINT范围", exception);
        }
    }

    private static void validatePositiveBusinessId(String value, String fieldName) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(fieldName + " 必须是正十进制字符串");
        }
    }
}
