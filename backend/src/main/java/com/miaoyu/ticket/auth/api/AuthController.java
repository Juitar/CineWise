package com.miaoyu.ticket.auth.api;

import com.miaoyu.ticket.auth.application.AuthApplicationService;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.LoginCommand;
import com.miaoyu.ticket.auth.application.LoginResult;
import com.miaoyu.ticket.auth.domain.LoginType;
import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import com.miaoyu.ticket.auth.infrastructure.security.AuthCookieManager;
import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.observability.TraceIdHolder;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证 HTTP 入口只负责 DTO 映射与 Cookie 生命周期，业务判断统一交给应用服务。 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthApplicationService authService;
    private final CurrentUserAccessor currentUserAccessor;
    private final AuthCookieManager cookieManager;
    private final CsrfTokenRepository csrfTokenRepository;
    private final AuthProperties properties;

    public AuthController(
            AuthApplicationService authService,
            CurrentUserAccessor currentUserAccessor,
            AuthCookieManager cookieManager,
            CsrfTokenRepository csrfTokenRepository,
            AuthProperties properties) {
        this.authService = authService;
        this.currentUserAccessor = currentUserAccessor;
        this.cookieManager = cookieManager;
        this.csrfTokenRepository = csrfTokenRepository;
        this.properties = properties;
    }

    @PostMapping("/auth/login/password")
    @SecurityRequirement(name = "csrfToken")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "登录成功并写入 JWT HttpOnly Cookie",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(ref = "#/components/schemas/ResultCurrentUserResponse"))),
        @ApiResponse(responseCode = "400", description = "101001 请求参数不合法"),
        @ApiResponse(responseCode = "401", description = "201001 邮箱或密码错误"),
        @ApiResponse(responseCode = "403", description = "201005 账号不可用；201009 CSRF Token 缺失或无效")
    })
    public Result<CurrentUserResponse> loginUser(
            @Valid @RequestBody PasswordLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        return login(request, LoginType.PASSWORD, servletRequest, servletResponse);
    }

    @PostMapping("/admin/auth/login")
    @SecurityRequirement(name = "csrfToken")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "管理员登录成功并写入 JWT HttpOnly Cookie",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(ref = "#/components/schemas/ResultCurrentUserResponse"))),
        @ApiResponse(responseCode = "400", description = "101001 请求参数不合法"),
        @ApiResponse(responseCode = "401", description = "201001 邮箱、密码或管理员角色校验失败"),
        @ApiResponse(responseCode = "403", description = "201005 账号不可用；201009 CSRF Token 缺失或无效")
    })
    public Result<CurrentUserResponse> loginAdmin(
            @Valid @RequestBody PasswordLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        return login(request, LoginType.ADMIN_PASSWORD, servletRequest, servletResponse);
    }

    @GetMapping("/auth/me")
    @SecurityRequirement(name = "cookieAuth")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "返回服务端确认的当前用户",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(ref = "#/components/schemas/ResultCurrentUserResponse"))),
        @ApiResponse(responseCode = "401", description = "201006 Cookie 会话缺失或已经失效")
    })
    public Result<CurrentUserResponse> currentUser() {
        long userId = currentUserAccessor.requireCurrentUserId();
        return Result.success(CurrentUserResponse.from(authService.getCurrentUser(userId)));
    }

    @PostMapping("/auth/logout")
    @Operation(
            description = "CSRF Token 必需；认证 Cookie 可选。未登录或会话已失效时仍幂等清理 Cookie。",
            security = @SecurityRequirement(name = "csrfToken"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "幂等登出完成并清除认证 Cookie",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(ref = "#/components/schemas/ResultLogoutResponse"))),
        @ApiResponse(responseCode = "403", description = "201009 CSRF Token 缺失或无效")
    })
    public Result<LogoutResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        currentUserAccessor.findCurrentUser().ifPresent(authService::logout);
        cookieManager.clearAccessToken(response);
        csrfTokenRepository.saveToken(null, request, response);
        return Result.success(new LogoutResponse(true));
    }

    @GetMapping("/auth/csrf")
    public Result<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return Result.success(new CsrfTokenResponse(csrfToken.getToken(), properties.csrfHeaderName()));
    }

    private Result<CurrentUserResponse> login(
            PasswordLoginRequest request,
            LoginType loginType,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        LoginResult result = authService.login(new LoginCommand(
                request.clientRequestId(),
                request.email(),
                request.password(),
                loginType,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"),
                TraceIdHolder.currentTraceId()));
        cookieManager.writeAccessToken(servletResponse, result.accessToken());
        csrfTokenRepository.saveToken(null, servletRequest, servletResponse);
        return Result.success(CurrentUserResponse.from(result.currentUser()));
    }
}
