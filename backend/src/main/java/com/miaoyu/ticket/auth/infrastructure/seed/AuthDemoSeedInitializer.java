package com.miaoyu.ticket.auth.infrastructure.seed;

import com.miaoyu.ticket.auth.application.AuthDemoSeedService;
import com.miaoyu.ticket.auth.application.AuthDemoSeedCommand;
import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 认证演示种子使用独立开关，日志只记录创建数量，不记录邮箱、密码或散列。 */
@Component
@ConditionalOnProperty(prefix = "cinewise.auth.demo-seed", name = "enabled", havingValue = "true")
public class AuthDemoSeedInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthDemoSeedInitializer.class);
    private final AuthDemoSeedService seedService;
    private final AuthProperties properties;

    public AuthDemoSeedInitializer(AuthDemoSeedService seedService, AuthProperties properties) {
        this.seedService = seedService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.DemoSeed seed = properties.demoSeed();
        int created = seedService.createMissingAccounts(new AuthDemoSeedCommand(
                seed.userEmail(),
                seed.userPassword(),
                seed.userNickname(),
                seed.adminEmail(),
                seed.adminPassword(),
                seed.adminNickname(),
                seed.privacyPolicyVersion()));
        LOGGER.info("认证演示账号初始化完成, createdCount={}", created);
    }
}
