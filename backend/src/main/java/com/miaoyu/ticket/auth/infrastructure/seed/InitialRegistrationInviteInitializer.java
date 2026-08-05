package com.miaoyu.ticket.auth.infrastructure.seed;

import com.miaoyu.ticket.auth.application.InitialRegistrationInviteCommand;
import com.miaoyu.ticket.auth.application.InitialRegistrationInviteService;
import com.miaoyu.ticket.auth.infrastructure.config.RegistrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 仅在私有开关显式开启时创建唯一的培训邀请码，不记录任何敏感配置。 */
@Component
@ConditionalOnProperty(
        prefix = "cinewise.auth.registration.initial-invite",
        name = "enabled",
        havingValue = "true")
public class InitialRegistrationInviteInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialRegistrationInviteInitializer.class);
    private final InitialRegistrationInviteService service;
    private final RegistrationProperties properties;

    public InitialRegistrationInviteInitializer(
            InitialRegistrationInviteService service, RegistrationProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        RegistrationProperties.InitialInvite invite = properties.initialInvite();
        boolean created = service.createIfMissing(new InitialRegistrationInviteCommand(
                invite.code(), invite.maxUses(), invite.validFrom(), invite.expireTime()));
        LOGGER.info("首个培训邀请码受控初始化完成, created={}", created);
    }
}
