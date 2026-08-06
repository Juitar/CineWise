package com.miaoyu.ticket.auth.application.mail;

/** Provider 消息标识和错误码可用于恢复，但结果中不包含邮箱或模板内容。 */
public record EmailDeliveryResult(
        DeliveryResultStatus status, String providerMessageId, Integer errorCode) {

    public static EmailDeliveryResult sent(String providerMessageId) {
        return new EmailDeliveryResult(DeliveryResultStatus.SENT, providerMessageId, null);
    }

    public static EmailDeliveryResult failed(int errorCode) {
        return new EmailDeliveryResult(DeliveryResultStatus.FAILED, null, errorCode);
    }

    public static EmailDeliveryResult unknown() {
        return new EmailDeliveryResult(DeliveryResultStatus.UNKNOWN, null, null);
    }
}
