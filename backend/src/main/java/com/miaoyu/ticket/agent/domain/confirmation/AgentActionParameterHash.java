package com.miaoyu.ticket.agent.domain.confirmation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** 服务端已校验建单 Command 的版本化确定性摘要。 */
public record AgentActionParameterHash(String version, String value) {
    /** 当前持久化 hash 版本。 */
    public static final String VERSION_V1 = "v1";

    public AgentActionParameterHash {
        if (!VERSION_V1.equals(version)) {
            throw new IllegalArgumentException("参数摘要版本不支持");
        }
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("参数摘要格式不合法");
        }
    }

    /** 仅覆盖服务端受控的写参数，绝不纳入用户、金额、订单状态或前端摘要。 */
    public static AgentActionParameterHash from(ConfirmedOrderCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        String canonical = "toolName=" + command.toolName() + '\n'
                + "showId=" + command.showId() + '\n'
                + "seatIds=" + String.join(",", command.sortedSeatIds());
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return new AgentActionParameterHash(VERSION_V1, HexFormat.of().formatHex(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }
}
