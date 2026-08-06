package com.miaoyu.ticket.auth.infrastructure.mail;

import com.miaoyu.ticket.auth.application.mail.EmailDeliveryResult;
import com.miaoyu.ticket.auth.application.mail.EmailProviderPort;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** 开发/演示 Mock 按 deliveryKey 幂等记录结果，不连接网络且不得冒充真实邮件。 */
@Component
@ConditionalOnExpression("'${cinewise.auth.mail-delivery.enabled:false}' == 'true'"
        + " && '${cinewise.auth.mail-delivery.provider:smtp}' == 'mock'")
public class InMemoryEmailProviderAdapter implements EmailProviderPort {

    private final ConcurrentMap<String, EmailDeliveryResult> results = new ConcurrentHashMap<>();
    private final AtomicInteger deliveryCount = new AtomicInteger();

    @Override
    public EmailDeliveryResult send(ProviderEmail command) {
        return results.computeIfAbsent(command.deliveryKey(), ignored -> {
            deliveryCount.incrementAndGet();
            return EmailDeliveryResult.sent("mock-accepted");
        });
    }

    @Override
    public EmailDeliveryResult query(String deliveryKey) {
        return results.getOrDefault(deliveryKey, EmailDeliveryResult.unknown());
    }

    int deliveryCount() {
        return deliveryCount.get();
    }
}
