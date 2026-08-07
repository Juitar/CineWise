package com.miaoyu.ticket.job;

import com.miaoyu.ticket.ticketing.application.ExternalShowtimeImportTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 恢复既有异步导入任务，不创建新的导入范围。 */
@Component
public class ExternalShowtimeImportRecoveryJob {
    private final ExternalShowtimeImportTaskService taskService;

    public ExternalShowtimeImportRecoveryJob(ExternalShowtimeImportTaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelay = 60000)
    public void recover() { taskService.recover(); }
}
