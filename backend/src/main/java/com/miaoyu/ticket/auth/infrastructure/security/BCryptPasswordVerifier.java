package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.PasswordVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;

/** BCrypt 适配器集中处理空凭据，避免底层实现输出无意义告警或抛出异常。 */
final class BCryptPasswordVerifier implements PasswordVerifier {

    private final PasswordEncoder passwordEncoder;

    BCryptPasswordVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null
                && passwordHash != null
                && !passwordHash.isBlank()
                && passwordEncoder.matches(rawPassword, passwordHash);
    }

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
