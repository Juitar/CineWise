package com.miaoyu.ticket.profile.application;

import java.time.LocalDateTime;

/** C 在同意撤回事务提交后发送的最小通知，不携带邮箱、认证信息或原始画像内容。 */
public record ProfileDataConsentWithdrawnEvent(
    String eventId, long userId, long consentVersion, LocalDateTime occurredAt, String traceId) { }
