package com.miaoyu.ticket.travel.infrastructure;

import com.miaoyu.ticket.travel.application.TravelReminderSchedulingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 周期领取到期出行任务；业务正确性由任务版本和数据库条件更新保证，不依赖调度线程。
 *
 * <p>多个应用实例可同时执行本 Job，但只有取得任务版本的实例会写入建议快照。
 * Job 不读取订单、用户、位置或邮件数据，只把周期触发转交给 D 的应用服务。
 * 关闭公共 scheduling 开关时，该 Bean 的方法不会被 Spring 调度，便于受控测试窗口。</p>
 */
@Component
public class TravelReminderJob {
    private final TravelReminderSchedulingService schedulingService;

    public TravelReminderJob(TravelReminderSchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    /** 五分钟扫描一次，延迟由公共调度开关统一控制；邮件投递由 C 端口交付后另行接入。 */
    @Scheduled(
            initialDelayString = "${TRAVEL_REMINDER_DELAY_MILLISECONDS:300000}",
            fixedDelayString = "${TRAVEL_REMINDER_DELAY_MILLISECONDS:300000}")
    public void generateDueAdvice() { schedulingService.runDueTasks(); }
}
