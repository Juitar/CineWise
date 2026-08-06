package com.miaoyu.ticket.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.auth.application.AccessTokenService;
import com.miaoyu.ticket.auth.application.AuthUserRepository;
import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * 认证功能合并前的默认拒绝安全壳。C 应在本配置上接入 JWT Cookie、CSRF、401/403 处理器和角色规则，
 * 不应另建一条相互竞争的过滤链。
 */
@Configuration(proxyBeanMethods = false)
public class SecuritySkeletonConfiguration {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter,
            CsrfTokenRepository csrfTokenRepository,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(false))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/shows",
                                "/api/v1/shows/available-dates",
                                "/api/v1/shows/available-movies",
                                "/api/v1/shows/available-cinemas")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/movies",
                                "/api/v1/movies/*",
                                "/api/v1/cinemas",
                                "/api/v1/cinemas/*")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/email-codes",
                                "/api/v1/auth/register",
                                "/api/v1/auth/password/reset",
                                "/api/v1/auth/login/email",
                                "/api/v1/auth/login/password",
                                "/api/v1/admin/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/shows/*/seats")
                        .authenticated()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtCookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    @Bean
    public JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter(
            JwtDecoder jwtDecoder,
            AuthUserRepository userRepository,
            AccessTokenService accessTokenService,
            AuthCookieManager cookieManager,
            AuthProperties properties,
            java.time.Clock clock) {
        return new JwtCookieAuthenticationFilter(
                jwtDecoder, userRepository, accessTokenService, cookieManager, properties, clock);
    }

    /** 该过滤器只属于 Spring Security 链，禁止 Servlet 容器再自动注册并执行一次。 */
    @Bean
    public FilterRegistrationBean<JwtCookieAuthenticationFilter> disableJwtFilterServletRegistration(
            JwtCookieAuthenticationFilter filter) {
        FilterRegistrationBean<JwtCookieAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** CSRF Cookie 设为 HttpOnly；浏览器通过显式接口响应体取得 Token，不读取 Cookie。 */
    @Bean
    public CsrfTokenRepository csrfTokenRepository(AuthProperties properties) {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookieName(properties.csrfCookieName());
        repository.setHeaderName(properties.csrfHeaderName());
        repository.setCookieCustomizer(cookie -> cookie
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path("/"));
        return repository;
    }

    @Bean
    public AuthenticationEntryPoint authAuthenticationEntryPoint(ObjectMapper objectMapper) {
        AuthSecurityResponseWriter writer = new AuthSecurityResponseWriter(objectMapper);
        return (request, response, exception) -> writer.write(response, AuthErrorCode.SESSION_INVALID);
    }

    @Bean
    public AccessDeniedHandler authAccessDeniedHandler(ObjectMapper objectMapper) {
        AuthSecurityResponseWriter writer = new AuthSecurityResponseWriter(objectMapper);
        return (request, response, exception) -> writer.write(
                response,
                exception instanceof CsrfException ? AuthErrorCode.CSRF_INVALID : AuthErrorCode.FORBIDDEN);
    }
}
