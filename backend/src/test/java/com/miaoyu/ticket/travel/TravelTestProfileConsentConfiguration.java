package com.miaoyu.ticket.travel;

import com.miaoyu.ticket.profile.application.ProfileDataConsentQuery;
import com.miaoyu.ticket.profile.application.ProfileDataConsentSnapshot;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 出行 MySQL 集成测试覆盖支付和建议数据，不覆盖 C 的同意持久化；固定同意避免无关的画像写入前置条件干扰。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TravelTestProfileConsentConfiguration {

  @Bean
  @Primary
  ProfileDataConsentQuery travelTestProfileDataConsentQuery() {
    return userId -> new ProfileDataConsentSnapshot(true, 1L, 0L, null, null);
  }
}
