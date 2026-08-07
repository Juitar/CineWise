package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 领取到期任务生成建议，并在观影结束两小时后关闭任务的 D 应用入口。
 *
 * <p>调度器只调用本服务，不能直接读写 Mapper；每个候选真正是否获胜仍由建议版本条件更新决定。
 * 任务失败保留为可恢复状态，不能因为天气或临时数据库异常把已支付订单相关任务直接标为失败。
 * 邮件投递、投递状态和 UNKNOWN 恢复属于 C 邮件端口接入后的独立步骤，本服务不触碰通知日志。</p>
 */
@Service
public class TravelReminderSchedulingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TravelReminderSchedulingService.class);
    private static final int BATCH_SIZE = 100;
    private static final long COMPLETE_AFTER_HOURS = 2L;
    private final TravelTaskRepository taskRepository;
    private final TravelAdviceService adviceService;
    private final TravelNotificationService notificationService;
    private final Clock clock;

    @Autowired
    public TravelReminderSchedulingService(
            TravelTaskRepository taskRepository, TravelAdviceService adviceService,
            TravelNotificationService notificationService, Clock clock) {
        this.taskRepository = taskRepository;
        this.adviceService = adviceService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /** 兼容仅验证建议生成的旧单元测试；生产装配始终注入投递服务。 */
    public TravelReminderSchedulingService(
            TravelTaskRepository taskRepository, TravelAdviceService adviceService, Clock clock) {
        this(taskRepository, adviceService, null, clock);
    }

    /** 单条异常不能终止本轮其他任务；版本条件更新保证多个调度实例最终只写一个快照。 */
    public void runDueTasks() {
        LocalDateTime now = now();
        for (TravelTaskRepository.TravelTaskSnapshot task : taskRepository.listDueForAdvice(now, BATCH_SIZE)) {
            try {
                TravelAdviceSnapshot snapshot = adviceService.generate(task.id());
                if (notificationService != null) {
                    notificationService.deliver(task, snapshot.taskVersion());
                }
            } catch (RuntimeException exception) {
                // 失败保留 PENDING，由下一轮按同一任务版本安全重试；日志只记录内部任务号。
                LOGGER.warn("出行提醒建议生成失败，等待下一轮重试, taskId={}, errorType={}",
                        task.id(), exception.getClass().getSimpleName());
            }
        }
        LocalDateTime elapsedBefore = now.minusHours(COMPLETE_AFTER_HOURS);
        for (Long taskId : taskRepository.listElapsedTaskIds(elapsedBefore, BATCH_SIZE)) {
            // 阈值只用于判断是否已结束；closed_at 必须记录本次真实关闭时间，不能提前两小时。
            taskRepository.completeIfElapsed(taskId, elapsedBefore, now);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }
}
