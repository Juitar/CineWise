package com.miaoyu.ticket.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.application.AccessTokenService;
import com.miaoyu.ticket.auth.application.AuthUserRepository;
import com.miaoyu.ticket.auth.application.IssuedAccessToken;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.domain.AccountStatus;
import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.slf4j.LoggerFactory;

class JwtCookieAuthenticationFilterRenewalTest {

    private static final Instant NOW = Instant.parse("2030-08-06T02:00:00Z");
    private static final Instant SESSION_STARTED_AT = NOW.minus(Duration.ofMinutes(20));

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final AuthUserRepository userRepository = mock(AuthUserRepository.class);
    private final AccessTokenService tokenService = mock(AccessTokenService.class);
    private final AuthProperties properties = properties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private JwtCookieAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtCookieAuthenticationFilter(
                decoder,
                userRepository,
                tokenService,
                new AuthCookieManager(properties, clock),
                properties,
                clock);
        when(userRepository.findById(1001L)).thenReturn(Optional.of(user(RoleCode.USER, AccountStatus.NORMAL, 7L)));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotRenewWhenMoreThanThresholdRemains() throws Exception {
        authenticate(jwt(NOW.plusSeconds(601), RoleCode.USER, 7L, SESSION_STARTED_AT));

        MockHttpServletResponse response = perform("GET", "/api/v1/auth/me", chain -> { });

        assertThat(response.getHeader("Set-Cookie")).isNull();
        verifyNoInteractions(tokenService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {600L, 599L})
    void shouldRenewAtOrBelowThreshold(long remainingSeconds) throws Exception {
        authenticate(jwt(NOW.plusSeconds(remainingSeconds), RoleCode.USER, 7L, SESSION_STARTED_AT));
        when(tokenService.renew(any(), any())).thenReturn(Optional.of(renewedToken()));

        MockHttpServletResponse response = perform("GET", "/api/v1/auth/me", chain -> { });

        assertThat(response.getHeader("Set-Cookie"))
                .contains("cinewise_access_token=renewed-token", "Max-Age=1800", "HttpOnly");
        verify(tokenService).renew(any(AuthUser.class), org.mockito.ArgumentMatchers.eq(SESSION_STARTED_AT));
    }

    @Test
    void shouldNotRenewWhenAbsoluteSessionLimitHasBeenReached() throws Exception {
        Instant startedAt = NOW.minus(Duration.ofHours(8));
        authenticate(jwt(NOW.plusSeconds(60), RoleCode.USER, 7L, startedAt));

        MockHttpServletResponse response = perform("GET", "/api/v1/auth/me", chain -> { });

        assertThat(response.getHeader("Set-Cookie")).isNull();
        verifyNoInteractions(tokenService);
    }

    @ParameterizedTest
    @MethodSource("excludedRequests")
    void shouldNotRenewOnAuthenticationAndCsrfEndpoints(String method, String path) throws Exception {
        authenticate(jwt(NOW.plusSeconds(60), RoleCode.USER, 7L, SESSION_STARTED_AT));

        MockHttpServletResponse response = perform(method, path, chain -> { });

        assertThat(response.getHeader("Set-Cookie")).isNull();
        verifyNoInteractions(tokenService);
    }

    @ParameterizedTest
    @CsvSource({
        "GET,/api/v1/auth/me",
        "GET,/api/v1/profile",
        "POST,/api/v1/orders"
    })
    void shouldRenewMeAndOrdinaryAuthenticatedRequests(String method, String path) throws Exception {
        authenticate(jwt(NOW.plusSeconds(60), RoleCode.USER, 7L, SESSION_STARTED_AT));
        when(tokenService.renew(any(), any())).thenReturn(Optional.of(renewedToken()));

        MockHttpServletResponse response = perform(method, path, chain -> { });

        assertThat(response.getHeader("Set-Cookie")).isNotNull();
    }

    @Test
    void shouldNotRenewCorsPreflight() throws Exception {
        authenticate(jwt(NOW.plusSeconds(60), RoleCode.USER, 7L, SESSION_STARTED_AT));

        MockHttpServletResponse response = perform("OPTIONS", "/api/v1/orders", chain -> { });

        assertThat(response.getHeader("Set-Cookie")).isNull();
        verifyNoInteractions(tokenService);
    }

    @Test
    void shouldWriteRenewalCookieBeforePostSseFilterChainContinues() throws Exception {
        authenticate(jwt(NOW.plusSeconds(60), RoleCode.USER, 7L, SESSION_STARTED_AT));
        when(tokenService.renew(any(), any())).thenReturn(Optional.of(renewedToken()));

        MockHttpServletResponse response = perform(
                "POST",
                "/api/v1/agent/sessions/session-1/messages/stream",
                chainResponse -> assertThat(chainResponse.getHeader("Set-Cookie"))
                        .contains("cinewise_access_token=renewed-token"));

        assertThat(response.getHeader("Set-Cookie")).isNotNull();
    }

    @Test
    void shouldKeepAuthenticatedRequestWhenRenewalSigningFails() throws Exception {
        authenticate(jwt(NOW.plusSeconds(60), RoleCode.USER, 7L, SESSION_STARTED_AT));
        when(tokenService.renew(any(), any())).thenThrow(new IllegalStateException("signer unavailable"));

        Logger logger = (Logger) LoggerFactory.getLogger(JwtCookieAuthenticationFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MockHttpServletResponse response;
        try {
            response = perform("GET", "/api/v1/auth/me", chain ->
                    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull());
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(response.getHeader("Set-Cookie")).isNull();
        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertThat(logs)
                .contains("认证会话续签失败")
                .doesNotContain(
                        "raw-token",
                        "existing-jti",
                        "sessionStartedAt",
                        "cinewise_access_token",
                        "user@cinewise.test");
    }

    @Test
    void shouldReadEpochSecondSessionClaimFromRealJwtAndRenew() throws Exception {
        Instant requestTime = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant startedAt = requestTime.minus(Duration.ofMinutes(20));
        AuthSecurityConfiguration configuration = new AuthSecurityConfiguration();
        var encoder = configuration.jwtEncoder(properties);
        var realDecoder = configuration.jwtDecoder(properties);
        IssuedAccessToken original = new JwtAccessTokenService(
                encoder, properties, Clock.fixed(startedAt, ZoneOffset.UTC)).issue(
                        user(RoleCode.USER, AccountStatus.NORMAL, 7L));
        AccessTokenService renewalService = new JwtAccessTokenService(
                encoder, properties, Clock.fixed(requestTime, ZoneOffset.UTC));
        JwtCookieAuthenticationFilter realFilter = new JwtCookieAuthenticationFilter(
                realDecoder,
                userRepository,
                renewalService,
                new AuthCookieManager(properties, Clock.fixed(requestTime, ZoneOffset.UTC)),
                properties,
                Clock.fixed(requestTime, ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setServletPath("/api/v1/auth/me");
        request.setCookies(new Cookie(properties.accessCookieName(), original.value()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        realFilter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(response.getHeader("Set-Cookie"))
                .contains("cinewise_access_token=", "Max-Age=1800", "HttpOnly")
                .doesNotContain(original.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid signature", "expired token"})
    void shouldTreatInvalidAndExpiredJwtAsAnonymous(String reason) throws Exception {
        when(decoder.decode("raw-token")).thenThrow(new JwtException(reason));

        MockHttpServletResponse response = perform("GET", "/api/v1/auth/me", chain -> { });

        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userRepository, tokenService);
    }

    @Test
    void shouldRejectInactiveRoleChangedAndVersionChangedAccountsBeforeRenewal() throws Exception {
        assertRejected(user(RoleCode.USER, AccountStatus.DISABLED, 7L), RoleCode.USER, 7L);
        reset(userRepository, decoder, tokenService);
        assertRejected(user(RoleCode.ADMIN, AccountStatus.NORMAL, 7L), RoleCode.USER, 7L);
        reset(userRepository, decoder, tokenService);
        assertRejected(user(RoleCode.USER, AccountStatus.NORMAL, 8L), RoleCode.USER, 7L);
    }

    @Test
    void shouldAuthenticateLegacyJwtWithoutRenewingIt() throws Exception {
        authenticate(jwt(NOW.plusSeconds(60), RoleCode.USER, 7L, null));

        MockHttpServletResponse response = perform("GET", "/api/v1/auth/me", chain -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(response.getHeader("Set-Cookie")).isNull();
        verifyNoInteractions(tokenService);
    }

    private void assertRejected(AuthUser repositoryUser, RoleCode tokenRole, long tokenVersion) throws Exception {
        when(userRepository.findById(1001L)).thenReturn(Optional.of(repositoryUser));
        authenticate(jwt(NOW.plusSeconds(60), tokenRole, tokenVersion, SESSION_STARTED_AT));

        MockHttpServletResponse response = perform("GET", "/api/v1/auth/me", chain -> { });

        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenService, never()).renew(any(), any());
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Jwt jwt) {
        when(decoder.decode("raw-token")).thenReturn(jwt);
    }

    private MockHttpServletResponse perform(String method, String path, ChainAssertion assertion) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setCookies(new Cookie(properties.accessCookieName(), "raw-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                assertion.accept((MockHttpServletResponse) servletResponse);
        filter.doFilter(request, response, chain);
        return response;
    }

    private Jwt jwt(Instant expiresAt, RoleCode role, long tokenVersion, Instant sessionStartedAt) {
        return Jwt.withTokenValue("raw-token")
                .header("alg", "HS256")
                .subject("1001")
                .claim("role", role.name())
                .claim("tokenVersion", tokenVersion)
                .claims(claims -> {
                    if (sessionStartedAt != null) {
                        claims.put(JwtAccessTokenService.SESSION_STARTED_AT_CLAIM, sessionStartedAt);
                    }
                })
                .issuedAt(NOW.minus(Duration.ofMinutes(20)))
                .expiresAt(expiresAt)
                .claim("jti", "existing-jti")
                .build();
    }

    private IssuedAccessToken renewedToken() {
        return new IssuedAccessToken("renewed-token", NOW, NOW.plus(Duration.ofMinutes(30)));
    }

    private AuthUser user(RoleCode role, AccountStatus status, long tokenVersion) {
        return new AuthUser(
                1001L,
                "user@cinewise.test",
                "hash",
                "测试用户",
                role,
                status,
                true,
                tokenVersion,
                "2030-08-06",
                LocalDateTime.of(2030, 8, 6, 2, 0));
    }

    private static AuthProperties properties() {
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

    private static Stream<Arguments> excludedRequests() {
        return Stream.of(
                Arguments.of("POST", "/api/v1/auth/login/password"),
                Arguments.of("POST", "/api/v1/auth/login/email"),
                Arguments.of("POST", "/api/v1/admin/auth/login"),
                Arguments.of("POST", "/api/v1/auth/register"),
                Arguments.of("POST", "/api/v1/auth/email-codes"),
                Arguments.of("POST", "/api/v1/auth/password/reset"),
                Arguments.of("POST", "/api/v1/auth/logout"),
                Arguments.of("GET", "/api/v1/auth/csrf"));
    }

    @FunctionalInterface
    private interface ChainAssertion {
        void accept(MockHttpServletResponse response) throws java.io.IOException;
    }
}
