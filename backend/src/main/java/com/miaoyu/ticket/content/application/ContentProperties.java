package com.miaoyu.ticket.content.application;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内容查询的可调配置。
 *
 * <p>Demo 有效期不写死在 Provider 中，后续缓存和快照使用同一时间基线时可以按环境调整，
 * 不需要改动业务代码。</p>
 *
 * <p>缓存有效期和最大陈旧期分开配置：前者控制 Redis 占用，后者控制用户是否还能看到已经过期的快照。</p>
 */
@ConfigurationProperties(prefix = "cinewise.content")
public record ContentProperties(Duration demoTtl, Duration cacheTtl, Duration maxStale) {

    /** Demo 数据必须有正有效期，避免启动后立刻被识别为过期内容。 */
    public ContentProperties {
        demoTtl = requirePositive(demoTtl, "demoTtl");
        cacheTtl = requirePositive(cacheTtl, "cacheTtl");
        maxStale = requirePositive(maxStale, "maxStale");
    }

    /**
     * 三个时间都必须为正数，避免缓存永不过期或过期快照被无限期复用。
     *
     * <p>启动时立即拒绝错误配置，比在一次内容查询中才暴露异常更容易定位环境问题。</p>
     */
    private static Duration requirePositive(Duration value, String fieldName) {
        value = Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
