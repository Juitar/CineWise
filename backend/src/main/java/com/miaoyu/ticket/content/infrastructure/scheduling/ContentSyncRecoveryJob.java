package com.miaoyu.ticket.content.infrastructure.scheduling;

import com.miaoyu.ticket.content.application.AdminContentSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 收敛因进程退出、网络阻塞或实例崩溃而遗留的管理员同步任务。
 *
 * <p>恢复器只把租约已过期的 RUNNING 改为 FAILED + INTERNAL，绝不重新调用 Provider。管理员必须以
 * 原 clientRequestId 查询终态；若需要新的同步，必须显式提交新的请求标识。</p>
 */
@Component
public class ContentSyncRecoveryJob {
    private final AdminContentSyncService service;

    public ContentSyncRecoveryJob(AdminContentSyncService service) { this.service = service; }

    /** 每分钟检查一次，配合九十秒租约避免把仍在运行的实例误判为失败。 */
    @Scheduled(fixedDelay = 60000)
    public void recoverExpiredTasks() { service.recoverExpiredSyncTasks(); }
}
