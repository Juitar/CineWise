package com.miaoyu.ticket.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** 重复登出也返回 true，客户端无需判断旧会话是否已经失效。 */
public record LogoutResponse(
        @Schema(description = "服务端已完成幂等登出和 Cookie 清理", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean loggedOut) {
}
