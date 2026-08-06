package com.miaoyu.ticket.auth.infrastructure.mail;

import com.miaoyu.ticket.auth.application.mail.EmailDeliveryResult;
import com.miaoyu.ticket.auth.application.mail.EmailProviderPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** 公共邮件未启用时失败关闭，禁止把未连接 SMTP 的调用报告为成功。 */
@Component
@ConditionalOnExpression("'${cinewise.auth.mail-delivery.enabled:false}' != 'true'")
public class UnavailableEmailProviderAdapter implements EmailProviderPort {

    static final int PROVIDER_UNAVAILABLE = 301105;

    @Override
    public EmailDeliveryResult send(ProviderEmail command) {
        return EmailDeliveryResult.failed(PROVIDER_UNAVAILABLE);
    }

    @Override
    public EmailDeliveryResult query(String deliveryKey) {
        return EmailDeliveryResult.unknown();
    }
}
