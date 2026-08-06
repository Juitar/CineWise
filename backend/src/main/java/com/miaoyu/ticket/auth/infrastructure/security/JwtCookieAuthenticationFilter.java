package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.AccessTokenService;
import com.miaoyu.ticket.auth.application.AuthUserRepository;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/** JWT 签名通过后仍回查账号状态、角色和 tokenVersion，数据库结果决定会话是否有效。 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtCookieAuthenticationFilter.class);
    private static final Set<String> RENEWAL_EXCLUDED_PATHS = Set.of(
            "/api/v1/auth/login/password",
            "/api/v1/auth/login/email",
            "/api/v1/admin/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/email-codes",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/logout",
            "/api/v1/auth/csrf");

    private final JwtDecoder jwtDecoder;
    private final AuthUserRepository userRepository;
    private final AccessTokenService accessTokenService;
    private final AuthCookieManager cookieManager;
    private final AuthProperties properties;
    private final Clock clock;
    private final String cookieName;

    public JwtCookieAuthenticationFilter(
            JwtDecoder jwtDecoder,
            AuthUserRepository userRepository,
            AccessTokenService accessTokenService,
            AuthCookieManager cookieManager,
            AuthProperties properties,
            Clock clock) {
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
        this.accessTokenService = accessTokenService;
        this.cookieManager = cookieManager;
        this.properties = properties;
        this.clock = clock;
        this.cookieName = properties.accessCookieName();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        findCookie(request).flatMap(this::authenticate).ifPresent(session -> {
            setAuthentication(session.currentUser());
            renewIfNeeded(request, response, session);
        });
        filterChain.doFilter(request, response);
    }

    private Optional<String> findCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private Optional<AuthenticatedSession> authenticate(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            long userId = Long.parseLong(jwt.getSubject());
            String role = jwt.getClaimAsString("role");
            Number tokenVersion = jwt.getClaim("tokenVersion");
            if (role == null || tokenVersion == null) {
                return Optional.empty();
            }
            return userRepository.findById(userId)
                    .filter(AuthUser::isActive)
                    .filter(user -> user.role().name().equals(role))
                    .filter(user -> user.tokenVersion() == tokenVersion.longValue())
                    .map(user -> new AuthenticatedSession(
                            user,
                            new CurrentUser(user.id(), user.role(), user.tokenVersion()),
                            jwt));
        } catch (JwtException | IllegalArgumentException exception) {
            // 无效 Cookie 按匿名处理，由实际受保护端点统一返回 201006，不向客户端暴露解析原因。
            return Optional.empty();
        }
    }

    private void setAuthentication(CurrentUser currentUser) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + currentUser.role().name()));
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(currentUser, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 续签 Cookie 必须在下游提交响应或建立 SSE 流之前完整写入。 */
    private void renewIfNeeded(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticatedSession session) {
        if (!isRenewableRequest(request)) {
            return;
        }
        Instant expiresAt = session.jwt().getExpiresAt();
        Instant sessionStartedAt = session.jwt().getClaimAsInstant(
                JwtAccessTokenService.SESSION_STARTED_AT_CLAIM);
        Instant now = clock.instant();
        if (expiresAt == null
                || sessionStartedAt == null
                || Duration.between(now, expiresAt).compareTo(properties.renewalThreshold()) > 0
                || !now.isBefore(sessionStartedAt.plus(properties.absoluteSessionTtl()))) {
            return;
        }
        try {
            accessTokenService.renew(session.user(), sessionStartedAt)
                    .ifPresent(token -> cookieManager.writeAccessToken(response, token));
        } catch (RuntimeException exception) {
            // 身份已经通过数据库复核，续签故障只告警；禁止记录 Token、Cookie 或声明原文。
            LOGGER.warn("认证会话续签失败，本次已验证请求继续处理");
        }
    }

    private boolean isRenewableRequest(HttpServletRequest request) {
        return !"OPTIONS".equalsIgnoreCase(request.getMethod())
                && !RENEWAL_EXCLUDED_PATHS.contains(request.getServletPath());
    }

    private record AuthenticatedSession(AuthUser user, CurrentUser currentUser, Jwt jwt) {
    }
}
