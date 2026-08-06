package com.miaoyu.ticket.profile.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** user_profile_tag 的持久化行，枚举转换只在 Repository 适配器完成。 */
public record ProfileTagRow(
    long id,
    long userId,
    String tagType,
    String tagValue,
    String polarity,
    BigDecimal weight,
    String source,
    BigDecimal confidence,
    String status,
    LocalDateTime expiresAt,
    long version,
    LocalDateTime updatedAt) { }
