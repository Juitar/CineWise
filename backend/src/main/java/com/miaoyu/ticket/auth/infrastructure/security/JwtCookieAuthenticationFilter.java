package com.miaoyu.ticket.auth.infrastructure.security;

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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/** JWT 签名通过后仍回查账号状态、角色和 tokenVersion，数据库结果决定会话是否有效。 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;
    private final AuthUserRepository userRepository;
    private final String cookieName;

    public JwtCookieAuthenticationFilter(
            JwtDecoder jwtDecoder, AuthUserRepository userRepository, AuthProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
        this.cookieName = properties.accessCookieName();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        findCookie(request).flatMap(this::authenticate).ifPresent(this::setAuthentication);
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

    private Optional<CurrentUser> authenticate(String token) {
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
                    .map(user -> new CurrentUser(user.id(), user.role(), user.tokenVersion()));
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
}
