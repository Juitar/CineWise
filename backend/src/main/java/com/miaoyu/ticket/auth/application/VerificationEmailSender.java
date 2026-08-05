package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.VerificationPurpose;

/** 邮件发送端口不负责保存验证码、限流或登录状态。 */
public interface VerificationEmailSender {

    DeliveryResult send(String normalizedEmail, String code, VerificationPurpose purpose, String traceId);

    enum DeliveryResult {
        SENT,
        FAILED,
        UNKNOWN
    }
}
