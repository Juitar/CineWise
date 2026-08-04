package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 认证 Cookie 属性集中生成，JWT 永远保持 HttpOnly 且不进入响应体。 */
@Component
public class AuthCookieManager {

    private final AuthProperties properties;

    public AuthCookieManager(AuthProperties properties) {
        this.properties = properties;
    }

    public void writeAccessToken(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, properties.accessTokenTtl()).toString());
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
}
