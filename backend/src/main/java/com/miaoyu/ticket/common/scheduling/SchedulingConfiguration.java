package com.miaoyu.ticket.common.scheduling;

import com.miaoyu.ticket.common.config.SchedulingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 单机 MVP 的公共调度线程池；业务 Job 仍须以数据库条件更新和唯一约束保证幂等。 */
@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class SchedulingConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulingConfiguration.class);

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(SchedulingProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.poolSize());
        scheduler.setThreadNamePrefix(properties.threadNamePrefix());
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(properties.shutdownAwaitSeconds());
        scheduler.setErrorHandler(error -> LOGGER.error("定时任务执行失败", error));
        return scheduler;
    }
}
