package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentSource;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 内容查询的统一来源封套，调用方据此识别时效和降级结果。
 *
 * <p>无论结果来自缓存、快照还是 Demo，都必须保留原始来源、数据时间和有效期；调用方不能只看
 * 数据是否存在就把过期内容当成当前事实。</p>
 *
 * <p>`degraded` 与 `fallbackType` 成对出现：前者供展示层判断是否提示降级，后者说明实际使用了
 * 缓存、快照还是 Mock，便于测试固定回退顺序。</p>
 */
public record ContentResult<T>(
        T data,
        ContentSource source,
        LocalDateTime dataTime,
        LocalDateTime expiresAt,
        boolean expired,
        boolean degraded,
        ContentFallbackType fallbackType) {

    /**
     * 统一校验时间顺序和降级标记，避免不同 Provider 返回彼此矛盾的来源封套。
     */
    public ContentResult {
        data = Objects.requireNonNull(data, "data must not be null");
        source = Objects.requireNonNull(source, "source must not be null");
        dataTime = Objects.requireNonNull(dataTime, "dataTime must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (expiresAt.isBefore(dataTime)) {
            throw new IllegalArgumentException("expiresAt must not be before dataTime");
        }
        if (degraded != (fallbackType != null)) {
            throw new IllegalArgumentException("degraded and fallbackType must be consistent");
        }
    }
}
