package com.miaoyu.ticket.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.application.IssuedAccessToken;
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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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

        IssuedAccessToken token = service.issue(user);
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("1001");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(((Number) jwt.getClaim("tokenVersion")).longValue()).isEqualTo(7L);
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getExpiresAt()).isEqualTo(issuedAt.plus(Duration.ofMinutes(30)));
        assertThat(jwt.getClaimAsInstant(JwtAccessTokenService.SESSION_STARTED_AT_CLAIM))
                .isEqualTo(issuedAt);
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder(
                "sub", "role", "tokenVersion", "sessionStartedAt", "iat", "exp", "jti");
    }

    @Test
    void shouldRenewWithOriginalSessionStartAndNewJti() throws Exception {
        AuthSecurityConfiguration configuration = new AuthSecurityConfiguration();
        JwtEncoder encoder = configuration.jwtEncoder(properties);
        JwtDecoder decoder = configuration.jwtDecoder(properties);
        Instant sessionStartedAt = Instant.now().minus(Duration.ofMinutes(20)).truncatedTo(ChronoUnit.SECONDS);
        Instant renewalTime = sessionStartedAt.plus(Duration.ofMinutes(20));
        JwtAccessTokenService service = new JwtAccessTokenService(
                encoder, properties, Clock.fixed(renewalTime, ZoneOffset.UTC));
        AuthUser user = user();

        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Jwt> renew = () -> decoder.decode(
                    service.renew(user, sessionStartedAt).orElseThrow().value());
            List<Jwt> renewed = executor.invokeAll(List.of(renew, renew)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();

            assertThat(renewed).allSatisfy(jwt -> {
                assertThat(jwt.getSubject()).isEqualTo("1001");
                assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
                assertThat(((Number) jwt.getClaim("tokenVersion")).longValue()).isEqualTo(7L);
                assertThat(jwt.getIssuedAt()).isEqualTo(renewalTime);
                assertThat(jwt.getExpiresAt()).isEqualTo(renewalTime.plus(Duration.ofMinutes(30)));
                assertThat(jwt.getClaimAsInstant(JwtAccessTokenService.SESSION_STARTED_AT_CLAIM))
                        .isEqualTo(sessionStartedAt);
            });
            assertThat(renewed.get(0).getId()).isNotEqualTo(renewed.get(1).getId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldCapRenewalAtAbsoluteSessionExpiryAndStopAtLimit() {
        AuthSecurityConfiguration configuration = new AuthSecurityConfiguration();
        JwtEncoder encoder = configuration.jwtEncoder(properties);
        Instant sessionStartedAt = Instant.now().minus(Duration.ofHours(7)).truncatedTo(ChronoUnit.SECONDS);
        Instant nearLimit = sessionStartedAt.plus(Duration.ofHours(7)).plus(Duration.ofMinutes(50));
        JwtAccessTokenService nearLimitService = new JwtAccessTokenService(
                encoder, properties, Clock.fixed(nearLimit, ZoneOffset.UTC));

        var capped = nearLimitService.renew(user(), sessionStartedAt).orElseThrow();

        assertThat(capped.expiresAt()).isEqualTo(sessionStartedAt.plus(Duration.ofHours(8)));
        JwtAccessTokenService atLimitService = new JwtAccessTokenService(
                encoder,
                properties,
                Clock.fixed(sessionStartedAt.plus(Duration.ofHours(8)), ZoneOffset.UTC));
        assertThat(atLimitService.renew(user(), sessionStartedAt)).isEmpty();
    }

    @Test
    void shouldWriteAndClearSecureHttpOnlyCookieWithExpectedAttributes() {
        Instant now = Instant.parse("2026-08-03T08:00:00Z");
        AuthCookieManager manager = new AuthCookieManager(properties, Clock.fixed(now, ZoneOffset.UTC));
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.writeAccessToken(response, new IssuedAccessToken(
                "jwt-value", now, now.plus(Duration.ofMinutes(30))));
        manager.clearAccessToken(response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies.get(0))
                .contains("cinewise_access_token=jwt-value", "HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=1800")
                .doesNotContain("Secure");
        assertThat(cookies.get(1)).contains("cinewise_access_token=", "Max-Age=0", "HttpOnly");
    }

    @Test
    void shouldUseActualTokenRemainingTimeForCookieMaxAge() {
        Instant now = Instant.parse("2026-08-03T08:00:00Z");
        AuthCookieManager manager = new AuthCookieManager(properties, Clock.fixed(now, ZoneOffset.UTC));
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.writeAccessToken(response, new IssuedAccessToken(
                "near-limit-token", now, now.plus(Duration.ofMinutes(7))));

        assertThat(response.getHeader("Set-Cookie"))
                .contains("Max-Age=420", "HttpOnly", "SameSite=Lax", "Path=/")
                .doesNotContain("Max-Age=1800");
    }

    private AuthUser user() {
        return new AuthUser(
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
    }

    private AuthProperties properties() {
        return new AuthProperties(
                "test-jwt-secret-at-least-32-bytes-long",
                "test-audit-secret-at-least-32-bytes-long",
                Duration.ofMinutes(30),
                Duration.ofMinutes(10),
                Duration.ofHours(8),
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
