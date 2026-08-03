package com.miaoyu.ticket.content.application;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内容查询的可调配置。
 *
 * <p>Demo 有效期不写死在 Provider 中，后续缓存和快照使用同一时间基线时可以按环境调整，
 * 不需要改动业务代码。</p>
 */
@ConfigurationProperties(prefix = "cinewise.content")
public record ContentProperties(Duration demoTtl) {

    /** Demo 数据必须有正有效期，避免启动后立刻被识别为过期内容。 */
    public ContentProperties {
        demoTtl = Objects.requireNonNull(demoTtl, "demoTtl must not be null");
        if (demoTtl.isNegative() || demoTtl.isZero()) {
            throw new IllegalArgumentException("demoTtl must be positive");
        }
    }
}
