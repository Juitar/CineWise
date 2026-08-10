package com.miaoyu.ticket.agent.application.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventStreamCursor;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEventDraft;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 同会话事件写入按会话行、游标行顺序串行化，避免 AUTO_INCREMENT 提前分配造成重放遗漏。 */
@Service
public class AgentRuntimeEventService {
    private static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private final AgentSessionRepository sessionRepository;
    private final AgentRunRepository runRepository;
    private final AgentRuntimeEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public AgentRuntimeEventService(
            AgentSessionRepository sessionRepository, AgentRunRepository runRepository,
            AgentRuntimeEventRepository eventRepository,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentRuntimeEvent append(
            AgentSession sourceSession, AgentRun run, AgentEventType type, AgentStoredJson payload) {
        // 先校验载荷和运行版本；旧计划、旧运行不能在新会话流里补写事件。
        validatePayload(payload);
        Objects.requireNonNull(run, "事件所属运行不能为空");
        Objects.requireNonNull(type, "事件类型不能为空");
        AgentRun currentRun = runRepository.findByRunIdAndUserId(run.runId(), run.userId())
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        if (currentRun.version() != run.version()
                || !Objects.equals(currentRun.planVersion(), run.planVersion())) {
            throw new IllegalStateException("Agent 运行计划已更新，拒绝写入旧事件");
        }
        Objects.requireNonNull(sourceSession, "事件来源会话不能为空");
        AgentSession session = sessionRepository
                .findBySessionIdAndUserIdForUpdate(sourceSession.sessionId(), run.userId())
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        // 会话行锁先于游标行锁取得，所有同会话写入遵循同一顺序以避免死锁。
        LocalDateTime now = run.updateTime();
        LocalDateTime cursorExpireAt = session.expireAt().isAfter(run.expireAt())
                ? session.expireAt()
                : run.expireAt();
        AgentEventStreamCursor cursor = eventRepository.findCursorForUpdate(session.sessionId()).orElseGet(() -> {
            // 首条事件先创建游标并重新加锁，不能假定并发事务中的新游标可直接使用。
            AgentEventStreamCursor created = new AgentEventStreamCursor(session.sessionId(), 0L, null, 0L,
                    cursorExpireAt, now, now);
            eventRepository.insertCursor(created);
            return eventRepository.findCursorForUpdate(session.sessionId()).orElseThrow();
        });
        LocalDateTime eventTime = now.isAfter(cursor.updateTime()) ? now : cursor.updateTime();
        // 事件时间单调递增，避免业务时钟回拨导致恢复顺序出现倒退。
        AgentRuntimeEvent event = eventRepository.append(new AgentRuntimeEventDraft(session.sessionId(), run.runId(),
                type, payload, run.expireAt(), eventTime));
        Long firstRetained = cursor.firstRetainedEventId() == null ? event.eventId() : cursor.firstRetainedEventId();
        AgentEventStreamCursor next = new AgentEventStreamCursor(session.sessionId(), event.eventId(), firstRetained,
                cursor.version() + 1, cursorExpireAt, cursor.createTime(), eventTime);
        if (!eventRepository.updateCursor(next, cursor.version())) {
            // 游标 CAS 失败表示并发写入已经抢先提交，当前事务必须整体回滚重试。
            throw new IllegalStateException("Agent 事件游标已由其他事务更新");
        }
        return event;
    }

    private void validatePayload(AgentStoredJson payload) {
        // SSE 恢复会持久化该 JSON，因此在写库前同时限制大小、结构和隐私字段。
        Objects.requireNonNull(payload, "Agent 事件载荷不能为空");
        byte[] bytes = payload.value().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Agent 事件载荷超过 16 KiB");
        }
        try {
            JsonNode node = objectMapper.readTree(payload.value());
            if (!node.isObject()) {
                throw new IllegalArgumentException("Agent 事件载荷必须是 JSON 对象");
            }
            rejectSensitiveFields(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Agent 事件载荷不是合法 JSON", exception);
        }
    }

    private static void rejectSensitiveFields(JsonNode node) {
        // 递归检查对象和数组，禁止认证凭证、模型提示词、位置和完整地址进入事件存储。
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (key.contains("token") || key.contains("secret") || key.contains("password")
                        || key.contains("cookie") || key.contains("authorization") || key.contains("prompt")
                        || key.contains("stacktrace") || key.contains("latitude") || key.contains("longitude")
                        || key.equals("address") || key.contains("distancecontextid")
                        || key.contains("distancepreference") || key.contains("locationresult")) {
                    throw new IllegalArgumentException("Agent 事件载荷包含禁止字段: " + entry.getKey());
                }
                rejectSensitiveFields(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(AgentRuntimeEventService::rejectSensitiveFields);
        }
    }
}
