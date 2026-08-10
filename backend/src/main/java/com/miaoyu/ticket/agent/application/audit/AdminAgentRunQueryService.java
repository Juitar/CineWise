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
        // 管理接口先在服务层复核角色，再读取轨迹，不能仅依赖控制器的权限标注。
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
        // 详情查询只返回脱敏后的摘要和节点状态，不返回用户输入、槽位快照或工具原始响应。
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
        // 会话可能已按保留期删除；审计页此时保留 runId 但不伪造 sessionId。
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
        // 节点统计从本次已读取的节点计算，避免详情页再查询一次持久化层。
        int completed = (int) nodes.stream()
                .filter(node -> node.status().equals(PlanNodeStatus.SUCCESS.name()))
                .count();
        int failed = (int) nodes.stream()
                .filter(node -> node.status().equals(PlanNodeStatus.FAILED.name()))
                .count();
        return new AdminAgentRunQueryRepository.NodeStats(nodes.size(), completed, failed);
    }

    private AdminAgentRunView.NodeView node(AgentRunStep step) {
        // 节点详情只映射执行状态和时间；输入引用、失败堆栈等敏感内容保持为空。
        return new AdminAgentRunView.NodeView(step.nodeId(), step.nodeType().name(), null,
                step.status().name(), step.attemptCount(), step.startedAt(), step.finishedAt(),
                duration(step.startedAt(), step.finishedAt()), null, null, null,
                step.recoveryPending() ? "运行恢复处理中" : null);
    }

    private Map<Long, UserAdminQueryPort.UserAdminSummary> users(List<AgentRun> runs) {
        // 使用有序去重集合批量查询用户目录，避免列表页产生 N+1 查询。
        Set<Long> ids = runs.stream().map(AgentRun::userId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return ids.isEmpty()
                ? Map.of()
                : Objects.requireNonNull(userAdminQueryPort.findByUserIds(Set.copyOf(ids)));
    }

    private Set<Long> resolveUserIds(String keyword) {
        // 关键字搜索结果限制数量，防止用户目录返回异常大集合直接拼进 SQL IN 条件。
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
        // 所有筛选、时间范围和分页都在这里归一化，仓储层只接收已经验证的 Criteria。
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
        // 状态值只能是枚举，非法字符串统一按请求参数错误处理。
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
        // 公开 runId 仅允许有限长度的非空值，避免无意义查询和日志污染。
        if (runId == null || runId.isBlank() || runId.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        return runId.trim();
    }

    private void requireAdmin() {
        // 再次从认证上下文取角色，不信任调用方传递的任何管理员标志。
        CurrentUser user = currentUserAccessor.requireCurrentUser();
        if (user.role() != RoleCode.ADMIN) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private static Long duration(LocalDateTime started, LocalDateTime finished) {
        // 运行未结束时不展示虚假的耗时，前端据此显示进行中状态。
        return finished == null ? null : Duration.between(started, finished).toMillis();
    }

    private static LocalDateTime toBusinessTime(java.time.OffsetDateTime value) {
        // API 时间统一转换为业务时区后再交给数据库筛选，避免浏览器时区影响审计结果。
        return value == null ? null : value.atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
    }

    private record NormalizedQuery(AgentRunStatus status, String userKeyword, LocalDateTime startedFrom,
            LocalDateTime startedTo, int page, int size, int offset) { }
}
