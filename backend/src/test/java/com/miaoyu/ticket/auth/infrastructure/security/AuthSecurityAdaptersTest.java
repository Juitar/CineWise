package com.miaoyu.ticket.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.domain.AccountStatus;
import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class AuthSecurityAdaptersTest {

    private final AuthProperties properties = properties();

    @Test
    void shouldEncodeAndDecodeMinimalJwtClaims() {
        AuthSecurityConfiguration configuration = new AuthSecurityConfiguration();
        JwtEncoder encoder = configuration.jwtEncoder(properties);
        JwtDecoder decoder = configuration.jwtDecoder(properties);
        Instant issuedAt = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        Clock clock = Clock.fixed(issuedAt, ZoneOffset.UTC);
        JwtAccessTokenService service = new JwtAccessTokenService(encoder, properties, clock);
        AuthUser user = new AuthUser(
                1001L,
                "user@cinewise.test",
                "hash",
                "测试用户",
                RoleCode.USER,
                AccountStatus.NORMAL,
                true,
                7L,
                "2026-08-03",
                LocalDateTime.of(2026, 8, 3, 8, 0));

        Jwt jwt = decoder.decode(service.issue(user));

        assertThat(jwt.getSubject()).isEqualTo("1001");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(((Number) jwt.getClaim("tokenVersion")).longValue()).isEqualTo(7L);
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getExpiresAt()).isEqualTo(issuedAt.plus(Duration.ofMinutes(30)));
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder(
                "sub", "role", "tokenVersion", "iat", "exp", "jti");
    }

    @Test
    void shouldWriteAndClearSecureHttpOnlyCookieWithExpectedAttributes() {
        AuthCookieManager manager = new AuthCookieManager(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.writeAccessToken(response, "jwt-value");
        manager.clearAccessToken(response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies.get(0))
                .contains("cinewise_access_token=jwt-value", "HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=1800")
                .doesNotContain("Secure");
        assertThat(cookies.get(1)).contains("cinewise_access_token=", "Max-Age=0", "HttpOnly");
    }

    private AuthProperties properties() {
        return new AuthProperties(
                "test-jwt-secret-at-least-32-bytes-long",
                "test-audit-secret-at-least-32-bytes-long",
                Duration.ofMinutes(30),
                "cinewise_access_token",
                "cinewise_csrf",
                "X-XSRF-TOKEN",
                false,
                "Lax",
                30,
                86_400_000L,
                new AuthProperties.DemoSeed(false, "", "", "", "", "", "", ""));
    }
}
