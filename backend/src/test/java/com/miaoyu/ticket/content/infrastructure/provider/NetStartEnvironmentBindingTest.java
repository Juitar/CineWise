package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@ActiveProfiles("test")
@SpringBootTest(properties = "cinewise.scheduling.enabled=false")
@ContextConfiguration(initializers = NetStartEnvironmentBindingTest.NetStartEnvironmentInitializer.class)
class NetStartEnvironmentBindingTest {

    @Autowired
    private NetStartProperties properties;

    @Autowired
    private Environment environment;

    @Test
    void givenNetStartEnvironmentVariables_whenPropertiesBind_thenOnlyNetStartSwitchesAreEnabled() {
        // 测试上下文会加载仓库真实 application.yml；不手写 NetStart 或虚拟线程的 YAML 默认值。
        assertThat(properties.enabled()).isTrue();
        assertThat(properties.syncOnStartup()).isTrue();
        assertThat(properties.baseUrl()).isEqualTo("http://203.0.113.1");
        // 若环境变量再次误放到 spring.threads.virtual，下列断言会直接失败。
        assertThat(environment.getProperty("spring.threads.virtual.enabled")).isEqualTo("false");
    }

    /** 以系统环境属性源模拟部署变量，验证真实 YAML 占位符的绑定位置。 */
    static class NetStartEnvironmentInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            context.getEnvironment().getPropertySources().addFirst(
                    new SystemEnvironmentPropertySource("test-netstart-environment", Map.of(
                            "CINEWISE_CONTENT_NETSTART_ENABLED", "true",
                            "CINEWISE_CONTENT_NETSTART_SYNC_ON_STARTUP", "true",
                            "CINEWISE_CONTENT_NETSTART_BASE_URL", "http://203.0.113.1")));
        }
    }
}
