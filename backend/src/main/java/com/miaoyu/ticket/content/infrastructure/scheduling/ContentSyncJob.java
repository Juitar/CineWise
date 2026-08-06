package com.miaoyu.ticket.content.infrastructure.scheduling;

import com.miaoyu.ticket.content.application.ContentSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 内容同步的调度入口。
 *
 * <p>Job 只触发 Application Service；Provider 调用、缓存写入和审计均留在各自分层，
 * 因此调度线程不会绕过关闭开关或把外部 JSON 写入任何存储。</p>
 */
@Component
public class ContentSyncJob {
    private final ContentSyncService service;

    public ContentSyncJob(ContentSyncService service) { this.service = service; }

    /** 默认每天凌晨执行一次；每轮只处理当前热映目录的一分钟详情预算，下一轮继续未完成身份。 */
    @Scheduled(cron = "${cinewise.content.netstart.daily-sync-cron}")
    public void synchronizeDailyContent() { service.synchronizeCurrentHotMovies(); }
}
