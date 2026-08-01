package com.miaoyu.ticket.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 统一业务时钟，测试可替换为固定 Clock。 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    public static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock businessClock() {
        return Clock.system(BUSINESS_ZONE_ID);
    }
}
