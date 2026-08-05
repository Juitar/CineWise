package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.VerificationPurpose;

/** 验证码发送命令携带限流和排查所需摘要来源，不进入持久化。 */
public record SendEmailCodeCommand(
        String email,
        VerificationPurpose purpose,
        String remoteAddress,
        String traceId) {
}
