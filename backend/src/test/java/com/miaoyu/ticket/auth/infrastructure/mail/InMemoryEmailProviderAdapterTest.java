package com.miaoyu.ticket.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.mail.DeliveryResultStatus;
import com.miaoyu.ticket.auth.application.mail.EmailProviderPort;
import org.junit.jupiter.api.Test;

class InMemoryEmailProviderAdapterTest {

    @Test
    void shouldDeduplicateAndQueryByDeliveryKey() {
        InMemoryEmailProviderAdapter adapter = new InMemoryEmailProviderAdapter();
        EmailProviderPort.ProviderEmail command = new EmailProviderPort.ProviderEmail(
                "delivery-1", "private@cinewise.test", "主题", "正文", "trace-1");

        assertThat(adapter.send(command).status()).isEqualTo(DeliveryResultStatus.SENT);
        assertThat(adapter.send(command).status()).isEqualTo(DeliveryResultStatus.SENT);
        assertThat(adapter.deliveryCount()).isEqualTo(1);
        assertThat(adapter.query("delivery-1").status()).isEqualTo(DeliveryResultStatus.SENT);
        assertThat(adapter.query("missing").status()).isEqualTo(DeliveryResultStatus.UNKNOWN);
    }
}
