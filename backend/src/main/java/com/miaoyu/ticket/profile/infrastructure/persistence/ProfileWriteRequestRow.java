package com.miaoyu.ticket.profile.infrastructure.persistence;

import java.time.LocalDateTime;

/** profile_write_request 的最小读取行，不携带原始请求内容。 */
public record ProfileWriteRequestRow(
    long userId,
    String operation,
    String idempotencyKey,
    String requestHash,
    int httpStatus,
    String responseJson,
    LocalDateTime expiresAt) { }
