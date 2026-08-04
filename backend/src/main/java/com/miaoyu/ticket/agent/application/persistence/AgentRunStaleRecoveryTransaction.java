package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将一条已确认陈旧的运行安全结束；不调用模型、工具或 D 的任何能力。 */
@Service
public class AgentRunStaleRecoveryTransaction {
    private static final String SAFE_FAILURE_TEXT = "智能助手暂时无法完成本次请求，请稍后重试。";
    private static final AgentStoredJson SAFE_FAILURE_PAYLOAD = new AgentStoredJson("{\"reason\":\"RUN_STALE\"}");

    private final AgentRunRepository runRepository;
    private final AgentRunStepRepository stepRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentSessionRepository sessionRepository;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public AgentRunStaleRecoveryTransaction(
            AgentRunRepository runRepository,
            AgentRunStepRepository stepRepository,
            AgentMessageRepository messageRepository,
            AgentSessionRepository sessionRepository,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 任何步骤或运行 CAS 失败都会回滚整个恢复短事务，避免留下半条恢复记录。 */
    @Transactional
    public void recover(AgentRun run) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        for (AgentRunStep step : stepRepository.findByRunId(run.id())) {
            if (!isUnfinished(step.status())) {
                continue;
            }
            AgentRunStep failedStep = failedStep(step, now);
            if (!stepRepository.updateWithCas(failedStep, step.version(), step.status())) {
                throw new AgentStaleRecoveryConcurrentException();
            }
        }
        AgentRun failedRun = new AgentRun(
                run.id(), run.runId(), run.sessionId(), run.userId(), run.clientRequestId(), run.requestHash(),
                run.planId(), run.planVersion(), AgentRunStatus.FAILED, run.traceId(), run.startedAt(), now,
                run.version() + 1, run.createTime(), now, run.expireAt());
        if (!runRepository.updateTerminalWithCas(failedRun, run.version())) {
            throw new AgentStaleRecoveryConcurrentException();
        }
        messageRepository.insert(new AgentMessage(
                idGenerator.nextId(), UUID.randomUUID().toString(), run.sessionId(), run.id(), run.userId(),
                AgentMessageRole.ASSISTANT, AgentMessageType.ERROR, SAFE_FAILURE_TEXT, SAFE_FAILURE_PAYLOAD,
                AgentMessageStatus.COMPLETED, now, now, run.expireAt()));
        sessionRepository.releaseActiveRun(run.sessionId(), run.id());
    }

    private static boolean isUnfinished(PlanNodeStatus status) {
        return status == PlanNodeStatus.PENDING || status == PlanNodeStatus.RUNNING;
    }

    private static AgentRunStep failedStep(AgentRunStep step, LocalDateTime now) {
        LocalDateTime startedAt = step.startedAt() == null ? now : step.startedAt();
        return new AgentRunStep(
                step.id(), step.runId(), step.planVersion(), step.nodeId(), step.nodeType(), step.dependsOn(),
                step.inputRefs(), PlanNodeStatus.FAILED, step.failurePolicy(), Math.max(1, step.attemptCount()),
                step.retryCount(), false, false, null, null, step.slotSnapshot(), startedAt, now,
                step.version() + 1, step.createTime(), now, step.expireAt());
    }
}
