package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.run.ExecutionNodeState;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用运行版本 CAS 保存重规划；旧计划记录只读保留，不能被新版本覆盖。 */
@Service
public class AgentRunReplanTransaction {
    private final AgentRunRepository runRepository;
    private final AgentRunStepRepository stepRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentPersistenceJsonFactory jsonFactory;
    private final AgentRuntimeEventService runtimeEventService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public AgentRunReplanTransaction(
            AgentRunRepository runRepository,
            AgentRunStepRepository stepRepository,
            AgentSessionRepository sessionRepository,
            AgentPersistenceJsonFactory jsonFactory,
            AgentRuntimeEventService runtimeEventService,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.sessionRepository = sessionRepository;
        this.jsonFactory = jsonFactory;
        this.runtimeEventService = runtimeEventService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 保存已校验且已提升版本的计划。CAS 未命中时不发布事件，调用方必须重新读取当前运行，不能补写旧计划。
     */
    @Transactional
    public AgentRun record(AgentRun run, ExecutionRunState state) {
        Objects.requireNonNull(run, "运行不能为空");
        Objects.requireNonNull(state, "重规划状态不能为空");
        if (run.status() != AgentRunStatus.RUNNING) {
            throw new IllegalStateException("非运行中任务不能重规划");
        }
        if (run.planVersion() == null || state.plan().version() <= run.planVersion()) {
            throw new IllegalArgumentException("重规划版本必须提升");
        }
        LocalDateTime now = now();
        AgentRun nextRun = new AgentRun(
                run.id(), run.runId(), run.sessionId(), run.userId(), run.clientRequestId(), run.requestHash(),
                state.plan().planId(), state.plan().version(), AgentRunStatus.RUNNING, run.traceId(), run.startedAt(),
                null, run.version() + 1, run.createTime(), now, run.expireAt());
        if (!runRepository.updateRunningPlanWithCas(nextRun, run.version())) {
            throw new IllegalStateException("Agent 运行已由其他事务处理");
        }
        Set<String> preservedSuccessIds = stepRepository.findByRunId(run.id()).stream()
                .filter(step -> step.status() == PlanNodeStatus.SUCCESS)
                .map(AgentRunStep::nodeId)
                .collect(Collectors.toUnmodifiableSet());
        stepRepository.insertAll(state.plan().nodes().stream()
                .filter(node -> !preservedSuccessIds.contains(node.nodeId()))
                .map(node -> toStep(run, state, node, now))
                .toList());
        AgentSession session = sessionRepository.findByIdAndUserId(run.sessionId(), run.userId())
                .orElseThrow(() -> new IllegalStateException("运行所属会话不存在"));
        runtimeEventService.append(session, nextRun, AgentEventType.PLAN_CREATED,
                jsonFactory.eventPayload(Map.of("planVersion", state.plan().version())));
        return nextRun;
    }

    private AgentRunStep toStep(AgentRun run, ExecutionRunState state, ExecutionPlanNode node, LocalDateTime now) {
        ExecutionNodeState nodeState = state.nodeState(node.nodeId());
        PlanNodeStatus status = nodeState.status();
        return new AgentRunStep(
                idGenerator.nextId(), run.id(), state.plan().version(), node.nodeId(), node.type(),
                jsonFactory.dependencies(node), jsonFactory.inputReferences(node), status, node.failurePolicy(),
                nodeState.attemptCount(), nodeState.retryCount(), status == PlanNodeStatus.RUNNING,
                nodeState.autoSkipped(), nodeState.skipReason(), nodeState.skipSourceNodeId(),
                jsonFactory.slotSnapshot(node), startedAt(status, now), finishedAt(status, now), 0L, now, now,
                run.expireAt());
    }

    private static LocalDateTime startedAt(PlanNodeStatus status, LocalDateTime now) {
        return status == PlanNodeStatus.PENDING
                        || status == PlanNodeStatus.WAITING_CONFIRMATION
                        || status == PlanNodeStatus.SKIPPED
                ? null : now;
    }

    private static LocalDateTime finishedAt(PlanNodeStatus status, LocalDateTime now) {
        return status == PlanNodeStatus.SUCCESS || status == PlanNodeStatus.FAILED || status == PlanNodeStatus.SKIPPED
                ? now : null;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }
}
