package com.miaoyu.ticket.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 公共投递默认关闭；SMTP、发件人和 D 确认的模板内容都必须显式配置。 */
@ConfigurationProperties(prefix = "cinewise.auth.mail-delivery")
public record EmailDeliveryProperties(
        boolean enabled, String from, Template viewingReminder) {

    public EmailDeliveryProperties {
        viewingReminder = viewingReminder == null ? new Template("", "") : viewingReminder;
    }

    public record Template(String subject, String body) {
    }
}
