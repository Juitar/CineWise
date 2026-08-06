package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 取消只跳过尚未开始的节点；不尝试中断已经运行的只读工具。 */
@Service
public class AgentRunCancellationService {
    private static final AgentStoredJson CANCELLED_EVENT_PAYLOAD = new AgentStoredJson("{\"status\":\"CANCELLED\"}");
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentRunRepository runRepository;
    private final AgentRunStepRepository stepRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentRuntimeEventService runtimeEventService;
    private final Clock clock;

    public AgentRunCancellationService(
            CurrentUserAccessor currentUserAccessor,
            AgentRunRepository runRepository,
            AgentRunStepRepository stepRepository,
            AgentSessionRepository sessionRepository,
            AgentRuntimeEventService runtimeEventService,
            Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.sessionRepository = sessionRepository;
        this.runtimeEventService = runtimeEventService;
        this.clock = clock;
    }

    @Transactional
    public AgentRun cancelMyRun(String runId) {
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentRun run = runRepository.findByRunIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        if (run.status().isTerminal()) {
            return run;
        }
        LocalDateTime now = now();
        stepRepository.findByRunId(run.id()).stream()
                .filter(step -> step.status() == PlanNodeStatus.PENDING
                        || step.status() == PlanNodeStatus.WAITING_CONFIRMATION)
                .forEach(step -> stepRepository.updateWithCas(
                        cancelledStep(step, now), step.version(), step.status()));
        AgentRun cancelled = cancelledRun(run, now);
        if (!runRepository.updateTerminalWithCas(cancelled, run.version())) {
            return runRepository.findByRunIdAndUserId(runId, userId)
                    .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        }
        AgentSession session = sessionRepository.findByIdAndUserId(run.sessionId(), userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        runtimeEventService.append(session, cancelled, AgentEventType.RUN_COMPLETE, CANCELLED_EVENT_PAYLOAD);
        sessionRepository.releaseActiveRun(run.sessionId(), run.id());
        return cancelled;
    }

    private static AgentRun cancelledRun(AgentRun run, LocalDateTime now) {
        return new AgentRun(run.id(), run.runId(), run.sessionId(), run.userId(), run.clientRequestId(),
                run.requestHash(), run.planId(), run.planVersion(), AgentRunStatus.CANCELLED, run.traceId(),
                run.startedAt(), now, run.version() + 1, run.createTime(), now, run.expireAt());
    }

    private static AgentRunStep cancelledStep(AgentRunStep step, LocalDateTime now) {
        return new AgentRunStep(step.id(), step.runId(), step.planVersion(), step.nodeId(), step.nodeType(),
                step.dependsOn(), step.inputRefs(), PlanNodeStatus.SKIPPED, step.failurePolicy(), 0, step.retryCount(),
                false, false, null, null, step.slotSnapshot(), null, now, step.version() + 1,
                step.createTime(), now, step.expireAt());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }
}
