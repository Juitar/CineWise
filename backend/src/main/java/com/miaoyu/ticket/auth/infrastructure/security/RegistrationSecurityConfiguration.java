package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.RegistrationInviteHasher;
import com.miaoyu.ticket.auth.infrastructure.config.RegistrationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 注册摘要组件只接收启动时已校验的安全配置。 */
@Configuration(proxyBeanMethods = false)
public class RegistrationSecurityConfiguration {

    @Bean
    public RegistrationInviteHasher registrationInviteHasher(RegistrationProperties properties) {
        return new HmacRegistrationInviteHasher(properties.inviteHashSecret());
    }
}
