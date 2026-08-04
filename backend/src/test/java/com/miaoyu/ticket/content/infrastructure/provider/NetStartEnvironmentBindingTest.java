package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class NetStartEnvironmentBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NetStartPropertiesConfiguration.class))
            .withPropertyValues(
                    "cinewise.content.netstart.base-url=https://apis.netstart.cn/maoyan",
                    "cinewise.content.netstart.daily-sync-cron=0 0 3 * * *",
                    "cinewise.content.netstart.connect-timeout=500ms",
                    "cinewise.content.netstart.read-timeout=1500ms",
                    "cinewise.content.netstart.requests-per-minute=10",
                    "cinewise.content.netstart.retry-count=1",
                    "cinewise.content.netstart.retry-backoff=200ms",
                    "spring.threads.virtual.enabled=false")
            .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                    new SystemEnvironmentPropertySource("test-netstart-environment", Map.of(
                            "CINEWISE_CONTENT_NETSTART_ENABLED", "true",
                            "CINEWISE_CONTENT_NETSTART_SYNC_ON_STARTUP", "true"))));

    @Test
    void givenNetStartEnvironmentVariables_whenPropertiesBind_thenOnlyNetStartSwitchesAreEnabled() {
        contextRunner.run(context -> {
            NetStartProperties properties = context.getBean(NetStartProperties.class);

            // 模拟操作系统环境变量，避免测试只验证 YAML 的短横线属性而遗漏实际部署名称。
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.syncOnStartup()).isTrue();
            // NetStart 开关绝不能改变整个应用的虚拟线程模型。
            assertThat(context.getEnvironment().getProperty("spring.threads.virtual.enabled")).isEqualTo("false");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NetStartProperties.class)
    static class NetStartPropertiesConfiguration {
    }
}
