package com.miaoyu.ticket.agent.application.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final int EVENT_LIMIT = 500;
    private static final int MAX_USER_IDS = 100;
    private static final String USER_UNAVAILABLE = "用户不可用";
    private static final String TOOL_FAILURE = "工具查询失败";
    private final CurrentUserAccessor currentUserAccessor;
    private final UserAdminQueryPort userAdminQueryPort;
    private final AdminAgentRunQueryRepository repository;
    private final AgentRunStepRepository stepRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentRuntimeEventRepository eventRepository;
    private final ApiProperties apiProperties;
    private final ObjectMapper objectMapper;

    public AdminAgentRunQueryService(CurrentUserAccessor currentUserAccessor, UserAdminQueryPort userAdminQueryPort,
            AdminAgentRunQueryRepository repository, AgentRunStepRepository stepRepository,
            AgentSessionRepository sessionRepository, AgentRuntimeEventRepository eventRepository,
            ApiProperties apiProperties, ObjectMapper objectMapper) {
        this.currentUserAccessor = currentUserAccessor;
        this.userAdminQueryPort = userAdminQueryPort;
        this.repository = repository;
        this.stepRepository = stepRepository;
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.apiProperties = apiProperties;
        this.objectMapper = objectMapper;
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
        return new AdminAgentRunPageView(total, normalized.page(), normalized.size(),
                runs.stream().map(run -> summary(run, users.get(run.userId()), List.of())).toList());
    }

    @Transactional(readOnly = true)
    public AdminAgentRunView queryRun(String runId) {
        requireAdmin();
        AgentRun run = repository.findByRunId(requiredRunId(runId))
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        List<AgentRunStep> steps = stepRepository.findByRunId(run.id());
        Map<String, ToolEvent> tools = toolEvents(run.runId());
        UserAdminQueryPort.UserAdminSummary user = users(List.of(run)).get(run.userId());
        return summary(run, user, steps.stream().map(step -> node(step, tools.get(step.nodeId()))).toList());
    }

    private AdminAgentRunView summary(AgentRun run, UserAdminQueryPort.UserAdminSummary user,
            List<AdminAgentRunView.NodeView> nodes) {
        int completed = (int) nodes.stream()
                .filter(node -> node.status().equals(PlanNodeStatus.SUCCESS.name()))
                .count();
        int failed = (int) nodes.stream()
                .filter(node -> node.status().equals(PlanNodeStatus.FAILED.name()))
                .count();
        Long duration = duration(run.startedAt(), run.finishedAt());
        String sessionId = sessionRepository.findById(run.sessionId())
                .map(session -> session.sessionId())
                .orElse(null);
        return new AdminAgentRunView(run.runId(), sessionId,
                user == null ? USER_UNAVAILABLE : user.emailMasked(), run.status().name(), run.planId(),
                run.planVersion(), nodes.size(), completed, failed, run.startedAt(), run.finishedAt(), duration,
                null, run.status() == AgentRunStatus.FAILED ? TOOL_FAILURE : null, nodes);
    }

    private AdminAgentRunView.NodeView node(AgentRunStep step, ToolEvent tool) {
        String targetName = tool == null ? step.nodeType().name() : tool.toolName();
        String toolStatus = tool == null ? null : tool.status();
        String errorSummary = tool != null && tool.failed() ? TOOL_FAILURE : null;
        return new AdminAgentRunView.NodeView(step.nodeId(), step.nodeType().name(), targetName,
                step.status().name(), step.attemptCount(), step.startedAt(), step.finishedAt(),
                duration(step.startedAt(), step.finishedAt()), toolStatus, null, errorSummary,
                step.recoveryPending() ? "运行恢复处理中" : null);
    }

    private Map<String, ToolEvent> toolEvents(String runId) {
        Map<String, ToolEvent> events = new LinkedHashMap<>();
        for (var event : eventRepository.findByRunId(runId, 0L, EVENT_LIMIT)) {
            if (event.type() != AgentEventType.TOOL_START && event.type() != AgentEventType.TOOL_COMPLETE
                    && event.type() != AgentEventType.TOOL_ERROR) {
                continue;
            }
            try {
                JsonNode payload = objectMapper.readTree(event.payload().value());
                String nodeId = text(payload, "nodeId");
                String toolName = text(payload, "toolName");
                if (nodeId == null || toolName == null) {
                    continue;
                }
                String status = event.type() == AgentEventType.TOOL_ERROR ? "FAILED"
                        : event.type() == AgentEventType.TOOL_COMPLETE ? "SUCCESS" : "RUNNING";
                events.put(nodeId, new ToolEvent(toolName, status, event.type() == AgentEventType.TOOL_ERROR));
            } catch (Exception ignored) {
                // 不可信或已损坏事件绝不进入管理响应，也不回传原文。
            }
        }
        return Map.copyOf(events);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
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

    private record ToolEvent(String toolName, String status, boolean failed) { }
    private record NormalizedQuery(AgentRunStatus status, String userKeyword, LocalDateTime startedFrom,
            LocalDateTime startedTo, int page, int size, int offset) { }
}
