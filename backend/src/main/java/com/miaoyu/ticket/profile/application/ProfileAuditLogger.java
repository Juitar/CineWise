package com.miaoyu.ticket.profile.application;

import java.util.Map;

/** 画像审计端口，只接收已通过字段白名单过滤的最小内容。 */
public interface ProfileAuditLogger {
  void record(Map<String, String> fields);
}
