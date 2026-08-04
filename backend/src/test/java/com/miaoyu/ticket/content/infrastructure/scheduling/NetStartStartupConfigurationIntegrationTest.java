package com.miaoyu.ticket.content.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.common.scheduling.SchedulingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "cinewise.content.netstart.sync-on-startup=true",
        "cinewise.scheduling.enabled=false"
})
class NetStartStartupConfigurationIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void givenOneTimeStartupSwitchAndDisabledScheduling_whenContextStarts_thenOnlyStartupEntryIsRegistered() {
        // Provider 的 enabled 仍使用默认 false，测试上下文不会访问外网或写入真实基础服务。
        assertThat(context.getBeansOfType(NetStartStartupSyncRunner.class)).hasSize(1);
        // 关闭配置类本身，保证本次验证不会遗漏任何其他领域的 @Scheduled 任务。
        assertThat(context.getBeansOfType(SchedulingConfiguration.class)).isEmpty();
    }
}
