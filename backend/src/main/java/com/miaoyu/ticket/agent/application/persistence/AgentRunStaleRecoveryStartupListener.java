package com.miaoyu.ticket.agent.application.persistence;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 应用就绪后执行一次陈旧运行恢复；不注册后台循环或定时调度。 */
@Component
public class AgentRunStaleRecoveryStartupListener {
    private final AgentRunStaleRecoveryService staleRecoveryService;

    public AgentRunStaleRecoveryStartupListener(AgentRunStaleRecoveryService staleRecoveryService) {
        this.staleRecoveryService = staleRecoveryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterApplicationReady() {
        staleRecoveryService.recoverStaleRuns();
    }
}
