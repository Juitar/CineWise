package com.miaoyu.ticket.auth.infrastructure.mail;

import com.miaoyu.ticket.auth.application.VerificationEmailSender;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 未显式启用 SMTP 时失败关闭，禁止把未发送的验证码报告为成功。 */
@Component
@ConditionalOnProperty(
        prefix = "cinewise.auth.verification.mail",
        name = "smtp-enabled",
        havingValue = "false",
        matchIfMissing = true)
public class UnavailableVerificationEmailSender implements VerificationEmailSender {

    @Override
    public DeliveryResult send(
            String normalizedEmail, String code, VerificationPurpose purpose, String traceId) {
        return DeliveryResult.FAILED;
    }
}
