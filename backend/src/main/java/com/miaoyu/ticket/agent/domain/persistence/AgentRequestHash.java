package com.miaoyu.ticket.agent.domain.persistence;

import java.util.Objects;

/** V008 保存的请求摘要；规范化 JSON 与 SHA-256 计算由后续 Application 服务负责。 */
public record AgentRequestHash(String version, String value) {
    private static final String VERSION_V1 = "v1";
    private static final String SHA_256_LOWERCASE_HEX = "[0-9a-f]{64}";

    public AgentRequestHash {
        Objects.requireNonNull(version, "请求摘要版本不能为空");
        Objects.requireNonNull(value, "请求摘要不能为空");
        if (!VERSION_V1.equals(version)) {
            throw new IllegalArgumentException("请求摘要版本必须为 v1");
        }
        if (!value.matches(SHA_256_LOWERCASE_HEX)) {
            throw new IllegalArgumentException("请求摘要必须是 64 位小写 SHA-256 十六进制值");
        }
    }
}
