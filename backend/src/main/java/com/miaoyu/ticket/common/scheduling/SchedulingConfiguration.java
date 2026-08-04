package com.miaoyu.ticket.common.scheduling;

import com.miaoyu.ticket.common.config.SchedulingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 单机 MVP 的公共调度线程池；业务 Job 仍须以数据库条件更新和唯一约束保证幂等。
 *
 * <p>关闭 `cinewise.scheduling.enabled` 时连同 `@EnableScheduling` 一并不注册，供受控测试窗口
 * 避免自动任务与手工单次同步同时执行。</p>
 */
@EnableScheduling
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cinewise.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {

    /** 自动任务的异常只记录到日志，不能让一个领域任务终止整个调度器。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulingConfiguration.class);

    /**
     * 创建应用共用的调度线程池。
     *
     * <p>仅当自动调度开启时才创建此 Bean；关闭时，一次性启动同步仍直接调用应用服务，
     * 因而不会依赖线程池或偷偷恢复任何周期任务。</p>
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(SchedulingProperties properties) {
        // 池大小和线程名前缀由统一配置管理，领域 Job 不各自创建无界线程。
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.poolSize());
        scheduler.setThreadNamePrefix(properties.threadNamePrefix());
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(properties.shutdownAwaitSeconds());
        // 单次失败必须可见，但由下轮扫描和各领域幂等规则恢复，不能杀死后续任务。
        scheduler.setErrorHandler(error -> LOGGER.error("定时任务执行失败", error));
        return scheduler;
    }
}
