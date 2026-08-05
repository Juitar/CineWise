package com.miaoyu.ticket.profile.infrastructure.persistence;

import java.time.LocalDateTime;

/** user_preference 的持久化行对象，仅在 infrastructure 内使用。 */
public record ProfilePreferenceRow(
    long userId, boolean personalizationEnabled, long version, LocalDateTime updatedAt) { }
