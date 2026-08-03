package com.miaoyu.ticket.auth.api;

/** 前端只把 token 保存在运行内存，并按 headerName 写入后续状态变更请求。 */
public record CsrfTokenResponse(String token, String headerName) {
}
