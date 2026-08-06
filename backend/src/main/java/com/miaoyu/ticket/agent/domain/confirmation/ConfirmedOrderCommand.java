package com.miaoyu.ticket.agent.domain.confirmation;

import com.miaoyu.ticket.agent.domain.tool.ToolCommand;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 已经由服务端校验、可用于创建确认动作的最小建单参数。 */
public record ConfirmedOrderCommand(String toolName, String showId, List<String> seatIds) implements ToolCommand {

    public ConfirmedOrderCommand {
        requireText(toolName, "toolName");
        requireDecimalId(showId, "showId");
        seatIds = List.copyOf(Objects.requireNonNull(seatIds, "seatIds 不能为空"));
        if (seatIds.isEmpty()) {
            throw new IllegalArgumentException("seatIds 不能为空");
        }
        seatIds.forEach(seatId -> requireDecimalId(seatId, "seatId"));
        if (seatIds.stream().distinct().count() != seatIds.size()) {
            throw new IllegalArgumentException("seatIds 不能重复");
        }
    }

    /** 摘要和写调用使用固定排序，禁止前端提交顺序改变确认含义。 */
    public List<String> sortedSeatIds() {
        return seatIds.stream().sorted(Comparator.comparingLong(Long::parseLong)).toList();
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    private static void requireDecimalId(String value, String fieldName) {
        requireText(value, fieldName);
        if (!value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(fieldName + " 必须是十进制字符串");
        }
    }
}
