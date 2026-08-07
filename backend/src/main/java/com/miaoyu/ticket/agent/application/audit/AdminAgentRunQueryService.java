package com.miaoyu.ticket.agent.application.audit;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.application.UserAdminQueryPort;
import com.miaoyu.ticket.common.config.ApiProperties;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理轨迹读取用例：先复核管理员，再在 Agent 自有端口读取并映射脱敏 DTO。 */
@Service
public class AdminAgentRunQueryService {
    private static final int MAX_USER_IDS = 100;
    private static final String USER_UNAVAILABLE = "用户不可用";
    private final CurrentUserAccessor currentUserAccessor;
    private final UserAdminQueryPort userAdminQueryPort;
    private final AdminAgentRunQueryRepository repository;
    private final AgentRunStepRepository stepRepository;
    private final AgentSessionRepository sessionRepository;
    private final ApiProperties apiProperties;

    public AdminAgentRunQueryService(CurrentUserAccessor currentUserAccessor, UserAdminQueryPort userAdminQueryPort,
            AdminAgentRunQueryRepository repository, AgentRunStepRepository stepRepository,
            AgentSessionRepository sessionRepository, ApiProperties apiProperties) {
        this.currentUserAccessor = currentUserAccessor;
        this.userAdminQueryPort = userAdminQueryPort;
        this.repository = repository;
        this.stepRepository = stepRepository;
        this.sessionRepository = sessionRepository;
        this.apiProperties = apiProperties;
    }

    @Transactional(readOnly = true)
    public AdminAgentRunPageView queryRuns(AdminAgentRunListQuery query) {
        requireAdmin();
        NormalizedQuery normalized = normalize(query);
        Set<Long> userIds = resolveUserIds(normalized.userKeyword());
        if (normalized.userKeyword() != null && userIds.isEmpty()) {
            return new AdminAgentRunPageView(0, normalized.page(), normalized.size(), List.of());
        }
        var criteria = new AdminAgentRunQueryRepository.Criteria(normalized.status(),
                normalized.userKeyword() == null ? null : userIds, normalized.startedFrom(), normalized.startedTo(),
                normalized.offset(), normalized.size());
        long total = repository.count(criteria);
        List<AgentRun> runs = repository.findPage(criteria);
        Map<Long, UserAdminQueryPort.UserAdminSummary> users = users(runs);
        Map<Long, AdminAgentRunQueryRepository.NodeStats> nodeStats = repository.findNodeStatsByRunIds(
                runs.stream().map(AgentRun::id).toList());
        return new AdminAgentRunPageView(total, normalized.page(), normalized.size(),
                runs.stream().map(run -> summary(run, users.get(run.userId()),
                        nodeStats.getOrDefault(run.id(), AdminAgentRunQueryRepository.NodeStats.EMPTY), List.of()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public AdminAgentRunView queryRun(String runId) {
        requireAdmin();
        AgentRun run = repository.findByRunId(requiredRunId(runId))
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        List<AgentRunStep> steps = stepRepository.findByRunId(run.id());
        UserAdminQueryPort.UserAdminSummary user = users(List.of(run)).get(run.userId());
        List<AdminAgentRunView.NodeView> nodes = steps.stream().map(this::node).toList();
        return summary(run, user, nodeStats(nodes), nodes);
    }

    private AdminAgentRunView summary(AgentRun run, UserAdminQueryPort.UserAdminSummary user,
            AdminAgentRunQueryRepository.NodeStats nodeStats, List<AdminAgentRunView.NodeView> nodes) {
        Long duration = duration(run.startedAt(), run.finishedAt());
        String sessionId = sessionRepository.findById(run.sessionId())
                .map(session -> session.sessionId())
                .orElse(null);
        return new AdminAgentRunView(run.runId(), sessionId,
                user == null ? USER_UNAVAILABLE : user.emailMasked(), run.status().name(), run.planId(),
                run.planVersion(), nodeStats.nodeCount(), nodeStats.completedNodeCount(), nodeStats.failedNodeCount(),
                run.startedAt(), run.finishedAt(), duration, null, null, nodes);
    }

    private static AdminAgentRunQueryRepository.NodeStats nodeStats(List<AdminAgentRunView.NodeView> nodes) {
        int completed = (int) nodes.stream()
                .filter(node -> node.status().equals(PlanNodeStatus.SUCCESS.name()))
                .count();
        int failed = (int) nodes.stream()
                .filter(node -> node.status().equals(PlanNodeStatus.FAILED.name()))
                .count();
        return new AdminAgentRunQueryRepository.NodeStats(nodes.size(), completed, failed);
    }

    private AdminAgentRunView.NodeView node(AgentRunStep step) {
        return new AdminAgentRunView.NodeView(step.nodeId(), step.nodeType().name(), null,
                step.status().name(), step.attemptCount(), step.startedAt(), step.finishedAt(),
                duration(step.startedAt(), step.finishedAt()), null, null, null,
                step.recoveryPending() ? "运行恢复处理中" : null);
    }

    private Map<Long, UserAdminQueryPort.UserAdminSummary> users(List<AgentRun> runs) {
        Set<Long> ids = runs.stream().map(AgentRun::userId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return ids.isEmpty()
                ? Map.of()
                : Objects.requireNonNull(userAdminQueryPort.findByUserIds(Set.copyOf(ids)));
    }

    private Set<Long> resolveUserIds(String keyword) {
        if (keyword == null) {
            return Set.of();
        }
        Set<Long> ids = Objects.requireNonNull(userAdminQueryPort.findUserIdsByKeyword(keyword));
        if (ids.size() > MAX_USER_IDS || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalStateException("用户目录返回了非法用户ID集合");
        }
        return Set.copyOf(ids);
    }

    private NormalizedQuery normalize(AdminAgentRunListQuery query) {
        if (query == null) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        AgentRunStatus status = parseStatus(query.status());
        String keyword = query.userKeyword() == null ? null : query.userKeyword().trim();
        if (keyword != null && keyword.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        LocalDateTime from = toBusinessTime(query.startedFrom());
        LocalDateTime to = toBusinessTime(query.startedTo());
        if (from != null && to != null && !to.isAfter(from)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        int page = query.page() == null ? apiProperties.defaultPage() : query.page();
        int size = query.size() == null ? apiProperties.defaultPageSize() : query.size();
        long offset = (long) (page - 1) * size;
        if (page < 1 || size < 1 || size > apiProperties.maxPageSize() || offset > Integer.MAX_VALUE) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        return new NormalizedQuery(status, keyword, from, to, page, size, (int) offset);
    }

    private static AgentRunStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AgentRunStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    private static String requiredRunId(String runId) {
        if (runId == null || runId.isBlank() || runId.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        return runId.trim();
    }

    private void requireAdmin() {
        CurrentUser user = currentUserAccessor.requireCurrentUser();
        if (user.role() != RoleCode.ADMIN) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private static Long duration(LocalDateTime started, LocalDateTime finished) {
        return finished == null ? null : Duration.between(started, finished).toMillis();
    }

    private static LocalDateTime toBusinessTime(java.time.OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
    }

    private record NormalizedQuery(AgentRunStatus status, String userKeyword, LocalDateTime startedFrom,
            LocalDateTime startedTo, int page, int size, int offset) { }
}
