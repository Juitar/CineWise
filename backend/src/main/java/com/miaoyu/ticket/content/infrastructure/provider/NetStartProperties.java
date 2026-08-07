package com.miaoyu.ticket.content.infrastructure.provider;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * NetStart 学习 Provider 的运行开关和本地保护参数。
 *
 * <p>这些值是本项目对第三方的保守保护，不代表对方公布的配额。dev profile 默认开启，正式环境
 * 仍会由 {@link NetStartContentProvider} 再次拦截，避免仅靠部署约定误开学习接口。</p>
 *
 * <p>一次性同步开关只用于受控验证窗口，不提供页面或公网调用入口。它必须与 enabled 同时为 true
 * 才会触发实际同步；任一开关关闭时，内容查询仍按缓存、快照和 Demo 回退。</p>
 *
 * <p>每日 cron 保持默认值，验证时可以关闭调度器并改用一次性同步，避免两个入口并发写入相同来源。</p>
 */
/**
 * @param enabled 是否允许学习 Provider 发起网络调用，dev profile 默认开启
 * @param syncOnStartup 是否在应用就绪后执行一次受控同步，默认关闭
 */
@ConfigurationProperties("cinewise.content.netstart")
public record NetStartProperties(boolean enabled, boolean syncOnStartup, String baseUrl, String dailySyncCron,
                                 Duration connectTimeout, Duration readTimeout,
                                 int requestsPerMinute, int retryCount, Duration retryBackoff,
                                 List<String> syncCities) {

    /** 兼容旧单元测试夹具；生产配置使用带 syncCities 的完整构造器。 */
    public NetStartProperties(boolean enabled, boolean syncOnStartup, String baseUrl, String dailySyncCron,
                              Duration connectTimeout, Duration readTimeout, int requestsPerMinute, int retryCount,
                              Duration retryBackoff) {
        this(enabled, syncOnStartup, baseUrl, dailySyncCron, connectTimeout, readTimeout, requestsPerMinute,
                retryCount, retryBackoff, List.of("430100"));
    }

    /**
     * 启动即拒绝危险参数，防止限流、超时或重试在运行时失效。
     *
     * <p>这里不接受运行时放宽请求次数或重试次数，避免测试配置把第三方保护阈值变成可随意绕过的值。</p>
     */
    @ConstructorBinding
    public NetStartProperties {
        baseUrl = requireText(baseUrl, "baseUrl");
        dailySyncCron = requireText(dailySyncCron, "dailySyncCron");
        connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        readTimeout = requirePositive(readTimeout, "readTimeout");
        retryBackoff = requirePositive(retryBackoff, "retryBackoff");
        syncCities = syncCities == null ? List.of("430100") : syncCities.stream()
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        if (requestsPerMinute != 10 || retryCount != 1) {
            throw new IllegalArgumentException("NetStart only permits 10 req/min and one retry");
        }
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static Duration requirePositive(Duration value, String name) {
        value = Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
