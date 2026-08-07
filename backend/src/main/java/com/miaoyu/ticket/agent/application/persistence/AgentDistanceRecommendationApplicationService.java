package com.miaoyu.ticket.agent.application;

import com.miaoyu.ticket.agent.api.AgentDistanceContextResponse;
import com.miaoyu.ticket.agent.api.AgentDistanceRecommendationResultRequest;
import com.miaoyu.ticket.agent.api.AgentDistanceRunInitializeRequest;
import com.miaoyu.ticket.agent.api.AgentDistanceRunResponse;
import com.miaoyu.ticket.agent.application.persistence.AgentDistanceRunInitializationService;
import com.miaoyu.ticket.agent.application.persistence.AgentInitialRunResult;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunResultTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeQueryService;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisor;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorRequest;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 距离推荐用例：等待阶段不触发模型；结果到达后只恢复原 run。
 *
 * <p>距离上下文只在调用 D 前保留于当前进程内存，持久化层及事件层均不接收该值。</p>
 */
@Service
public class AgentDistanceRecommendationApplicationService {
    private static final long RECOMMENDATION_DEADLINE_MS = 30_000L;

    private final CurrentUserAccessor currentUserAccessor;
    private final AgentDistanceRunInitializationService initializationService;
    private final AgentDistanceContextApplicationService contextService;
    private final AgentRuntimeQueryService queryService;
    private final AgentRunRepository runRepository;
    private final MultiToolSupervisor supervisor;
    private final AgentRunResultTransaction resultTransaction;
    private final Clock clock;

    public AgentDistanceRecommendationApplicationService(
            CurrentUserAccessor currentUserAccessor,
            AgentDistanceRunInitializationService initializationService,
            AgentDistanceContextApplicationService contextService,
            AgentRuntimeQueryService queryService,
            AgentRunRepository runRepository,
            MultiToolSupervisor supervisor,
            AgentRunResultTransaction resultTransaction,
            Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.initializationService = initializationService;
        this.contextService = contextService;
        this.queryService = queryService;
        this.runRepository = runRepository;
        this.supervisor = supervisor;
        this.resultTransaction = resultTransaction;
        this.clock = clock;
    }

    public AgentDistanceRunResponse initialize(String sessionId, AgentDistanceRunInitializeRequest request) {
        AgentInitialRunResult result = initializationService.initialize(
                sessionId, request.clientRequestId(), request.content(), request.context().entry());
        return response(result.run());
    }

    public AgentDistanceRunResponse findByClientRequest(String sessionId, String clientRequestId) {
        recoverExpiredWaitingRuns();
        return response(queryService.queryMyRunByClientRequest(sessionId, clientRequestId).run());
    }

    public AgentDistanceContextResponse createContext(String sessionId, String runId) {
        recoverExpiredWaitingRuns();
        AgentDistanceContextApplicationService.Created created = contextService.create(sessionId, runId);
        return new AgentDistanceContextResponse(
                created.distanceContextId(), created.expiresAt(), created.distancePreference());
    }

    /** 不自动重试；重复结果请求只返回已落库的真实状态。 */
    public AgentDistanceRunResponse submitLocationResult(
            String runId, AgentDistanceRecommendationResultRequest request) {
        recoverExpiredWaitingRuns();
        AgentRun run = requireWaitingRun(runId);
        if (run.status() != AgentRunStatus.WAITING_LOCATION) {
            return response(run);
        }
        if (request.locationResult() == AgentDistanceRecommendationResultRequest.LocationResult.UPLOADED) {
            if (!contextService.matches(runId, request.distanceContextId())) {
                throw new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
            }
            AgentDistanceRunResponse response = resume(
                    run, request.distanceContextId(), request.distancePreference());
            // D 成功消费后上下文通常已不存在；仅删除 B 的短暂索引，不将 ID 写入任何持久化对象。
            contextService.forget(runId);
            return response;
        }
        contextService.cleanupIfPresent(runId);
        return resume(run, null, null);
    }

    private AgentDistanceRunResponse resume(
            AgentRun waiting, String distanceContextId, String distancePreference) {
        AgentRun running = markRunning(waiting);
        if (running.status() != AgentRunStatus.RUNNING) {
            return response(running);
        }
        try {
            SlotSnapshot slots = new SlotSnapshot(1L, Map.of("context.entry", "workspace"));
            var result = supervisor.run(new MultiToolSupervisorRequest(
                    running.clientRequestId(), userContent(running),
                    new PlanValidationContext(Map.of(), Map.of(), slots), running.runId(), running.traceId(),
                    RECOMMENDATION_DEADLINE_MS, distanceContextId, distancePreference));
            return response(resultTransaction.record(running, result));
        } catch (RuntimeException exception) {
            resultTransaction.recordFailure(running);
            throw exception;
        }
    }

    private AgentRun markRunning(AgentRun run) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        AgentRun running = new AgentRun(
                run.id(), run.runId(), run.sessionId(), run.userId(), run.clientRequestId(), run.requestHash(),
                run.planId(), run.planVersion(), AgentRunStatus.RUNNING, run.traceId(), run.startedAt(), null,
                run.version() + 1, run.createTime(), now, run.expireAt());
        if (runRepository.updateWithCas(running, run.version(), AgentRunStatus.WAITING_LOCATION)) {
            return running;
        }
        return runRepository.findByRunIdAndUserId(run.runId(), run.userId())
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
    }

    /**
     * 超时扫描只由已认证用户的后续查询或操作触发；查询条件和 CAS 都使用 MySQL CURRENT_TIMESTAMP(3)。
     * 这样不会在没有认证上下文的后台线程中伪造 D 工具所需的当前用户。
     */
    private void recoverExpiredWaitingRuns() {
        long userId = currentUserAccessor.requireCurrentUserId();
        runRepository.findExpiredWaitingLocationByUser(userId, 100).forEach(waiting -> {
            AgentRun running = expiredWaitingToRunning(waiting);
            if (running.status() == AgentRunStatus.RUNNING) {
                // 超时不伪造重启前的 ID；当前进程仍有 ID 时才调用 D 清理。
                contextService.cleanupIfPresent(waiting.runId());
                resumeRunning(running, null, null);
            }
        });
    }

    private AgentRun expiredWaitingToRunning(AgentRun waiting) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        AgentRun running = new AgentRun(waiting.id(), waiting.runId(), waiting.sessionId(), waiting.userId(),
                waiting.clientRequestId(), waiting.requestHash(), waiting.planId(), waiting.planVersion(),
                AgentRunStatus.RUNNING, waiting.traceId(), waiting.startedAt(), null, waiting.version() + 1,
                waiting.createTime(), now, waiting.expireAt());
        if (runRepository.recoverExpiredWaitingLocationWithCas(running, waiting.version())) {
            return running;
        }
        return runRepository.findByRunIdAndUserId(waiting.runId(), waiting.userId()).orElse(waiting);
    }

    private void resumeRunning(AgentRun running, String distanceContextId,
            String distancePreference) {
        try {
            SlotSnapshot slots = new SlotSnapshot(1L, Map.of("context.entry", "workspace"));
            var result = supervisor.run(new MultiToolSupervisorRequest(running.clientRequestId(), userContent(running),
                    new PlanValidationContext(Map.of(), Map.of(), slots), running.runId(), running.traceId(),
                    RECOMMENDATION_DEADLINE_MS, distanceContextId, distancePreference));
            resultTransaction.record(running, result);
        } catch (RuntimeException exception) {
            resultTransaction.recordFailure(running);
        }
    }

    private AgentRun requireWaitingRun(String runId) {
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentRun run = runRepository.findByRunIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        return run;
    }

    private String userContent(AgentRun run) {
        return queryService.queryMyRun(run.runId()).messages().stream()
                .filter(message -> message.role() == com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole.USER)
                .map(com.miaoyu.ticket.agent.domain.persistence.AgentMessage::text)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("距离等待运行缺少用户消息"));
    }

    private AgentDistanceRunResponse response(AgentRun run) {
        long lastEventId = queryService.queryMyRun(run.runId()).lastEventId();
        return new AgentDistanceRunResponse(run.runId(), run.status().name(), Long.toString(lastEventId));
    }
}
