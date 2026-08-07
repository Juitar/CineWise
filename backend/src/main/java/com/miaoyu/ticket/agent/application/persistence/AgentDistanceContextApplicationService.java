package com.miaoyu.ticket.agent.application;

import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.recommendation.application.DistanceContextService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** B 仅在进程内暂存等待 run 的一次性上下文，绝不写入 Agent 持久化数据。 */
@Service
public class AgentDistanceContextApplicationService {
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentRunRepository runRepository;
    private final AgentSessionRepository sessionRepository;
    private final DistanceContextService distanceContextService;
    private final Map<String, Created> activeContexts = new ConcurrentHashMap<>();

    public AgentDistanceContextApplicationService(
            CurrentUserAccessor currentUserAccessor,
            AgentRunRepository runRepository,
            AgentSessionRepository sessionRepository,
            DistanceContextService distanceContextService) {
        this.currentUserAccessor = currentUserAccessor;
        this.runRepository = runRepository;
        this.sessionRepository = sessionRepository;
        this.distanceContextService = distanceContextService;
    }

    public Created create(String sessionId, String runId) {
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentRun run = runRepository.findByRunIdAndUserId(runId, userId)
                .orElseThrow(() -> unavailable());
        boolean sameSession = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .map(session -> session.id() == run.sessionId())
                .orElse(false);
        if (!sameSession) {
            throw unavailable();
        }
        if (run.status() != AgentRunStatus.WAITING_LOCATION) {
            throw new BusinessException(AgentErrorCode.ACTIVE_RUN_CONFLICT, "运行当前不能创建定位上下文");
        }
        return activeContexts.computeIfAbsent(runId, ignored -> createdByDistanceService(runId));
    }

    /** 拒绝、失败、超时和主动取消共用；D 返回 NOT_FOUND 也等同已经清理。 */
    public void cleanupIfPresent(String runId) {
        Created context = activeContexts.remove(runId);
        if (context != null) {
            distanceContextService.cleanup(context.distanceContextId(), runId);
        }
    }

    /** 上传成功后由 D 的推荐工具消费上下文，B 仅丢弃自己的短暂索引。 */
    public void forget(String runId) {
        activeContexts.remove(runId);
    }

    /** 结果回传只能引用本进程为同一 run 创建的上下文；重启后由 D 的 TTL 处理。 */
    public boolean matches(String runId, String distanceContextId) {
        Created context = activeContexts.get(runId);
        return context != null && context.distanceContextId().equals(distanceContextId);
    }

    private Created createdByDistanceService(String runId) {
        DistanceContextService.CreatedContext created = distanceContextService.createForRun(runId);
        return new Created(created.distanceContextId(), created.expiresAt(), "NEAREST");
    }

    private static BusinessException unavailable() {
        return new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
    }

    public record Created(String distanceContextId, Instant expiresAt, String distancePreference) {
    }
}
