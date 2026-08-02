package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 认证模块尚未合入时的安全兜底；C 提供正式身份 Bean 后自动让位，绝不伪造登录用户。 */
@Configuration(proxyBeanMethods = false)
public class CurrentUserAccessorFallbackConfiguration {

    @Bean
    @ConditionalOnMissingBean(CurrentUserAccessor.class)
    public CurrentUserAccessor unavailableCurrentUserAccessor() {
        return () -> {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        };
    }
}
