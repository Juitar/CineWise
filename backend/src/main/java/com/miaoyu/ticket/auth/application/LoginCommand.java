package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.LoginType;

/** 登录命令包含审计所需请求摘要，但不持久化原始密码或完整邮箱。 */
public record LoginCommand(
        String clientRequestId,
        String email,
        String password,
        LoginType loginType,
        String remoteAddress,
        String userAgent,
        String traceId) {
}
