package com.miaoyu.ticket.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** 前端只把 token 保存在运行内存，并按 headerName 写入后续状态变更请求。 */
public record CsrfTokenResponse(
        @Schema(description = "只保存在浏览器运行内存中的 CSRF Token", requiredMode = Schema.RequiredMode.REQUIRED)
        String token,
        @Schema(
                description = "写请求固定使用的 Header 名",
                example = "X-XSRF-TOKEN",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String headerName) {
}
