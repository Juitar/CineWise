package com.miaoyu.ticket.auth.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.miaoyu.ticket.auth.application.AccessTokenService;
import com.miaoyu.ticket.auth.application.LoginAuditSanitizer;
import com.miaoyu.ticket.auth.application.PasswordVerifier;
import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** 认证密码、JWT 和审计摘要使用框架标准实现，密钥只从已校验配置构造。 */
@Configuration(proxyBeanMethods = false)
public class AuthSecurityConfiguration {

    @Bean
    public PasswordEncoder authPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public PasswordVerifier passwordVerifier(PasswordEncoder passwordEncoder) {
        return new BCryptPasswordVerifier(passwordEncoder);
    }

    @Bean
    public JwtEncoder jwtEncoder(AuthProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties.jwtSecret())));
    }

    @Bean
    public JwtDecoder jwtDecoder(AuthProperties properties) {
        return NimbusJwtDecoder.withSecretKey(secretKey(properties.jwtSecret()))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public AccessTokenService accessTokenService(
            JwtEncoder jwtEncoder, AuthProperties properties, java.time.Clock clock) {
        return new JwtAccessTokenService(jwtEncoder, properties, clock);
    }

    @Bean
    public LoginAuditSanitizer loginAuditSanitizer(AuthProperties properties) {
        return new HmacLoginAuditSanitizer(properties.auditHashSecret());
    }

    private SecretKey secretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
