package com.miaoyu.ticket.profile.infrastructure.persistence;

import java.time.LocalDateTime;

/** user_behavior_event 的最小读取行，不暴露 payload_json。 */
public record ProfileBehaviorEventRow(
    String eventId,
    long userId,
    String eventType,
    String targetType,
    String targetId,
    LocalDateTime occurredAt) { }
