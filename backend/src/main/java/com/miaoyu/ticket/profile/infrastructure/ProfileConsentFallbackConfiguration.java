package com.miaoyu.ticket.profile.infrastructure;

import com.miaoyu.ticket.profile.application.ProfileDataConsentQuery;
import com.miaoyu.ticket.profile.application.ProfileDataConsentSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * C 的同意服务未部署时宁可拒绝写入，也不能把“无法查询”误当成已同意。
 * 测试和 C 的正式实现都可以用同类型 Bean 覆盖这个保守默认值。
 */
@Configuration
public class ProfileConsentFallbackConfiguration {
  @Bean
  @ConditionalOnMissingBean(ProfileDataConsentQuery.class)
  public ProfileDataConsentQuery deniedProfileDataConsentQuery() {
    return userId -> ProfileDataConsentSnapshot.notGranted();
  }
}
