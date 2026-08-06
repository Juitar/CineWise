package com.miaoyu.ticket.auth.application.mail;

/** 认证模块内部 Provider 边界；只有 C 的应用服务可以把已解析邮箱传入该端口。 */
public interface EmailProviderPort {

    EmailDeliveryResult send(ProviderEmail command);

    EmailDeliveryResult query(String deliveryKey);

    /** 已渲染内容只在认证基础设施内存中短暂存在，默认字符串输出必须脱敏。 */
    record ProviderEmail(
            String deliveryKey,
            String recipientEmail,
            String subject,
            String body,
            String traceId) {

        @Override
        public String toString() {
            return "ProviderEmail[deliveryKey=[REDACTED], recipientEmail=[REDACTED], content=[REDACTED], traceId="
                    + traceId + "]";
        }
    }
}
