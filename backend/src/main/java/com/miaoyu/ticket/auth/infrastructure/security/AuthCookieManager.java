package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.IssuedAccessToken;
import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 认证 Cookie 属性集中生成，JWT 永远保持 HttpOnly 且不进入响应体。 */
@Component
public class AuthCookieManager {

    private final AuthProperties properties;
    private final Clock clock;

    public AuthCookieManager(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void writeAccessToken(HttpServletResponse response, IssuedAccessToken token) {
        Duration remaining = remaining(token.expiresAt(), clock.instant());
        response.addHeader(HttpHeaders.SET_COOKIE, build(token.value(), remaining).toString());
    }

    public void clearAccessToken(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(properties.accessCookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private Duration remaining(Instant expiresAt, Instant now) {
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, expiresAt);
    }
}
