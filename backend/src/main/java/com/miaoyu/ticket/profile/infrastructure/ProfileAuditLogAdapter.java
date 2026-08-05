package com.miaoyu.ticket.profile.infrastructure;

import com.miaoyu.ticket.profile.application.ProfileAuditFieldPolicy;
import com.miaoyu.ticket.profile.application.ProfileAuditLogger;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 日志适配器在写日志前再次过滤字段，防止调用方误传隐私内容。 */
@Component
public class ProfileAuditLogAdapter implements ProfileAuditLogger {
  private static final Logger LOG = LoggerFactory.getLogger(ProfileAuditLogAdapter.class);

  @Override
  public void record(Map<String, String> fields) {
    LOG.info("profileAudit={}", filterFields(fields));
  }

  /**
   * 过滤逻辑保持为无副作用方法，测试可以直接确认日志内容不会携带画像原值。
   * 即使上层漏过滤，适配器也不会把敏感字段交给日志框架。
   */
  static Map<String, String> filterFields(Map<String, String> fields) {
    return fields.entrySet().stream()
        .filter(entry -> ProfileAuditFieldPolicy.isAllowed(entry.getKey()))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
