package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理本人会话的应用用例；列表和历史查询不触发运行、工具或事件。 */
@Service
public class AgentSessionManagementService {
    private static final int MAX_PAGE_SIZE = 100;
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRunRepository runRepository;
    private final Clock clock;

    public AgentSessionManagementService(
            CurrentUserAccessor currentUserAccessor,
            AgentSessionRepository sessionRepository,
            AgentMessageRepository messageRepository,
            AgentRunRepository runRepository,
            Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SessionPage listMySessions(int page, int size) {
        // 分页参数先限制范围，再用认证用户过滤，避免把其他用户的会话暴露给前端。
        int safeSize = checkedSize(size);
        int safePage = checkedPage(page);
        long userId = currentUserAccessor.requireCurrentUserId();
        long total = sessionRepository.countActiveByUserId(userId);
        return new SessionPage(total, safePage, safeSize,
                sessionRepository.findActiveByUserId(userId, offset(safePage, safeSize), safeSize));
    }

    @Transactional(readOnly = true)
    public MessagePage listMySessionMessages(String sessionId, int page, int size) {
        // 先验证会话归属，再查询消息和运行号；两次查询都带 userId/sessionId 防止越权拼接。
        int safeSize = checkedSize(size);
        int safePage = checkedPage(page);
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentSession session = requireActiveSession(sessionId, userId);
        long total = messageRepository.countBySessionIdAndUserId(session.id(), userId);
        List<AgentMessage> messages =
                messageRepository.findBySessionIdAndUserId(session.id(), userId, offset(safePage, safeSize), safeSize);
        Map<Long, String> publicRunIds = runRepository.findByIdsAndUserIdAndSessionId(
                        messages.stream().map(AgentMessage::runId).distinct().toList(), userId, session.id())
                .stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(AgentRun::id, AgentRun::runId));
        return new MessagePage(total, safePage, safeSize, messages.stream()
                .map(message -> new MessageRecord(message, requiredRunId(message, publicRunIds)))
                .toList());
    }

    @Transactional
    public ClearResult clearMySession(String sessionId) {
        // 有活动运行时拒绝清理，避免 SSE 仍在写入时把当前会话标记为历史。
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentSession session = requireActiveSession(sessionId, userId);
        if (session.activeRunId() != null) {
            throw new BusinessException(AgentErrorCode.ACTIVE_RUN_CONFLICT);
        }
        if (!clear(session, userId)) {
            throw resultAfterClearRace(sessionId, userId);
        }
        return new ClearResult(session.sessionId(), true);
    }

    @Transactional
    public BulkClearResult clearMySessions() {
        // 批量清理只处理没有活动运行的会话，跳过项由调用方展示给用户。
        long userId = currentUserAccessor.requireCurrentUserId();
        int clearedCount = 0;
        int skippedCount = 0;
        for (AgentSession session : sessionRepository.findAllActiveByUserId(userId)) {
            if (session.activeRunId() == null && clear(session, userId)) {
                clearedCount++;
            } else {
                skippedCount++;
            }
        }
        return new BulkClearResult(clearedCount, skippedCount);
    }

    private boolean clear(AgentSession session, long userId) {
        // 条件更新提供并发保护；成功后才过期关联消息、运行和事件数据。
        LocalDateTime now = now();
        if (!sessionRepository.clearIfActiveAndInactive(session.id(), userId, now)) {
            return false;
        }
        sessionRepository.expireSessionData(session.id(), session.sessionId(), now);
        return true;
    }

    private BusinessException resultAfterClearRace(String sessionId, long userId) {
        // 条件更新失败可能是并发启动运行，重新读取用于返回准确的冲突错误。
        AgentSession current = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        if (current.status() == AgentSessionStatus.ACTIVE && current.activeRunId() != null) {
            return new BusinessException(AgentErrorCode.ACTIVE_RUN_CONFLICT);
        }
        return new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
    }

    private AgentSession requireActiveSession(String sessionId, long userId) {
        // 已清理会话统一返回资源不存在，避免泄露历史会话的状态细节。
        AgentSession session = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        if (session.status() != AgentSessionStatus.ACTIVE) {
            throw new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
        }
        return session;
    }

    private static int checkedPage(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("page 必须大于零");
        }
        return page;
    }

    private static int checkedSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size 必须在 1 到 100 之间");
        }
        return size;
    }

    private static int offset(int page, int size) {
        // 使用精确乘法，页码溢出时直接失败而不是查询错误位置。
        return Math.multiplyExact(page - 1, size);
    }

    private static String requiredRunId(AgentMessage message, Map<Long, String> publicRunIds) {
        // 历史消息必须能映射到同一用户、同一会话的公开 runId，否则视为数据隔离异常。
        String runId = publicRunIds.get(message.runId());
        if (runId == null) {
            throw new IllegalStateException("历史消息关联的运行不属于当前用户或会话");
        }
        return runId;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    public record SessionPage(long total, int page, int size, List<AgentSession> records) {
    }

    public record MessagePage(long total, int page, int size, List<MessageRecord> records) {
    }

    public record MessageRecord(AgentMessage message, String runId) {
    }

    public record ClearResult(String sessionId, boolean cleared) {
    }

    public record BulkClearResult(int clearedCount, int skippedCount) {
    }
}
