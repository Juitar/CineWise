package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将已保存计划的当前运行暂停为等待定位。
 *
 * <p>此服务不接收位置、距离上下文或模型字段；待 C 确认的受控入口只能传可信 runId。进入等待时仅此一次
 * 更新 update_time，供数据库侧五分钟超时判断使用。</p>
 */
@Service
public class AgentLocationWaitingService {
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentRunRepository runRepository;
    private final Clock clock;

    public AgentLocationWaitingService(
            CurrentUserAccessor currentUserAccessor, AgentRunRepository runRepository, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.runRepository = runRepository;
        this.clock = clock;
    }

    @Transactional
    public AgentRun enterWaiting(String runId) {
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentRun run = runRepository.findByRunIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        if (run.status() == AgentRunStatus.WAITING_LOCATION) {
            return run;
        }
        if (run.status() != AgentRunStatus.RUNNING || run.planId() == null || run.planVersion() == null) {
            throw new BusinessException(AgentErrorCode.ACTIVE_RUN_CONFLICT, "运行当前不能等待定位");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        AgentRun waiting = new AgentRun(
                run.id(), run.runId(), run.sessionId(), run.userId(), run.clientRequestId(), run.requestHash(),
                run.planId(), run.planVersion(), AgentRunStatus.WAITING_LOCATION, run.traceId(), run.startedAt(), null,
                run.version() + 1, run.createTime(), now, run.expireAt());
        if (runRepository.updateWithCas(waiting, run.version(), AgentRunStatus.RUNNING)) {
            return waiting;
        }
        return runRepository.findByRunIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
    }
}
