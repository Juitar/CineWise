package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;

/** 在 D 合入正式实现前提供约定的 JDBC 兜底；正式 Bean 存在时自动让位。 */
@Configuration(proxyBeanMethods = false)
public class ContentSummaryQueryFallbackConfiguration {

    /** 仅在 D 尚未提供正式内容摘要端口时注册临时实现。 */
    @Bean
    @ConditionalOnMissingBean(ContentSummaryQueryPort.class)
    public ContentSummaryQueryPort contentSummaryQueryPort(JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcContentSummaryQueryAdapter(jdbcTemplate, clock);
    }
}
