package com.miaoyu.ticket.auth.application;

/** 邮箱验证码登录命令中的验证码只在当前调用内存中使用。 */
public record EmailCodeLoginCommand(
        String clientRequestId,
        String email,
        String code,
        String remoteAddress,
        String userAgent,
        String traceId) {
}
