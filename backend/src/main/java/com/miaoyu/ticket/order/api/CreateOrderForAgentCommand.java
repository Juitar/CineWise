package com.miaoyu.ticket.order.api;

import com.miaoyu.ticket.agent.domain.tool.ToolCommand;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 已确认 Agent 建单的最小业务参数。
 *
 * <p>确认凭证由 B 在进入 A 适配器前完成归属、哈希、版本和一次性消费校验；A 只接收其关联标识，
 * 不读取 B 的确认表。用户、金额和幂等标识不属于模型可控的命令字段。</p>
 *
 * <p>边界规则：</p>
 * <ul>
 *   <li>showId 和 seatIds 必须是正十进制字符串，避免 JavaScript/JSON 数值精度损失；</li>
 *   <li>一次最多六个且不得重复，防止未经确认的扩展参数进入锁座事务；</li>
 *   <li>金额和用户永远由 A 的权威数据及认证上下文产生；</li>
 *   <li>clientRequestId 和 idempotencyKey 只允许来自受信任的 ToolContext。</li>
 * </ul>
 */
public record CreateOrderForAgentCommand(
        String actionId,
        String showId,
        List<String> seatIds) implements ToolCommand {

    private static final int MAXIMUM_TICKET_COUNT = 6;
    private static final Pattern ACTION_ID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    public CreateOrderForAgentCommand {
        // actionId 只证明 B 调用的是已确认动作；A 不将其当作用户身份或数据库幂等键。
        validateActionId(actionId);
        // 在构造阶段拒绝模型容易伪造的符号、负数和超范围 ID，避免进入订单应用服务。
        requireText(showId, "showId");
        validatePositiveDecimalId(showId);
        if (seatIds == null || seatIds.isEmpty() || seatIds.size() > MAXIMUM_TICKET_COUNT) {
            throw new IllegalArgumentException("seatIds 数量必须在1到6之间");
        }
        // 防御性复制保证 B 完成参数哈希后，调用期间座位列表不会被外部集合修改。
        seatIds = List.copyOf(seatIds);
        seatIds.forEach(CreateOrderForAgentCommand::validatePositiveDecimalId);
        if (seatIds.stream().distinct().count() != seatIds.size()) {
            throw new IllegalArgumentException("seatIds 不能重复");
        }
    }

    /** 由 Adapter 在进入订单 Application Service 前转换为 A 的内部数字类型。 */
    public long parsedShowId() {
        return parsePositiveLong(showId, "showId");
    }

    /** 批量转换座位 ID；最终排序和状态校验仍由订单 Application Service 负责。 */
    public List<Long> parsedSeatIds() {
        return seatIds.stream().map(id -> parsePositiveLong(id, "seatId")).toList();
    }

    private static void validatePositiveDecimalId(String value) {
        parsePositiveLong(value, "业务ID");
    }

    private static void validateActionId(String actionId) {
        requireText(actionId, "actionId");
        if (!ACTION_ID_PATTERN.matcher(actionId).matches()) {
            throw new IllegalArgumentException("actionId必须是标准UUID");
        }
    }

    private static long parsePositiveLong(String value, String fieldName) {
        // 正则先排除 +1、-1、0 和非十进制形式，再由 Long.parseLong 校验 BIGINT 正数范围。
        requireText(value, fieldName);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(fieldName + "必须是正十进制字符串");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            // 不回显原始输入，避免异常消息把未经信任的 Agent 参数带入日志或 SSE。
            throw new IllegalArgumentException(fieldName + "超出BIGINT范围", exception);
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }
}
