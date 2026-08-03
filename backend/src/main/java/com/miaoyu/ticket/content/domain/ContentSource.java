package com.miaoyu.ticket.content.domain;

import java.util.Objects;

/**
 * 标准化内容的来源标识，避免业务层依赖外部厂商字段。
 *
 * <p>来源名称同时参与固定 Mock 数据的幂等身份；空名称会导致不同 Provider 或不同演示版本
 * 无法区分，因此在领域边界直接拒绝。</p>
 */
public record ContentSource(String name, ContentSourceType type) {

    /**
     * 归一化来源名称，保证缓存、快照和持久化层使用同一个稳定值。
     */
    public ContentSource {
        name = Objects.requireNonNull(name, "name must not be null").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        type = Objects.requireNonNull(type, "type must not be null");
    }
}
