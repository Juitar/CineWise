package com.miaoyu.ticket.profile.application;

import java.util.Set;

/** 画像审计只允许最小可排查字段，避免日志变成个人偏好副本。 */
public final class ProfileAuditFieldPolicy {
  private static final Set<String> ALLOWED =
      Set.of("userId", "operation", "result", "errorCode", "traceId");

  private ProfileAuditFieldPolicy() { }

  public static boolean isAllowed(String field) {
    return ALLOWED.contains(field);
  }
}
