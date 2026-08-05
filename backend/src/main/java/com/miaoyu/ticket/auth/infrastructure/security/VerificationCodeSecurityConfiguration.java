package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.VerificationCodeGenerator;
import com.miaoyu.ticket.auth.application.VerificationCodeHasher;
import com.miaoyu.ticket.auth.infrastructure.config.VerificationCodeProperties;
import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 验证码生成和摘要实现只依赖已校验配置，不暴露密钥或随机数实例。 */
@Configuration(proxyBeanMethods = false)
public class VerificationCodeSecurityConfiguration {

    @Bean
    public VerificationCodeGenerator verificationCodeGenerator(VerificationCodeProperties properties) {
        return new SecureNumericVerificationCodeGenerator(new SecureRandom(), properties.digits());
    }

    @Bean
    public VerificationCodeHasher verificationCodeHasher(VerificationCodeProperties properties) {
        return new HmacVerificationCodeHasher(properties.hashSecret());
    }
}
