package com.miaoyu.ticket.agent.application.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.agent.application.AgentErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将上一张服务端 QUESTION 的单项回答写入本人会话，模型原文不能直接成为槽位。 */
@Service
public class AgentConversationSlotService {
    private static final Pattern CITY_CODE = Pattern.compile("\\d{6}");
    private static final Pattern TICKET_COUNT = Pattern.compile("(?:[1-9]|1\\d|20)");
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentConversationSlotRepository slotRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AgentConversationSlotService(CurrentUserAccessor currentUserAccessor,
            AgentSessionRepository sessionRepository, AgentMessageRepository messageRepository,
            AgentConversationSlotRepository slotRepository, ObjectMapper objectMapper, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.slotRepository = slotRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 返回本轮唯一可用的服务端快照；无效回答保留旧值，由正常计划流程再次追问。 */
    @Transactional
    public SlotSnapshot prepare(String sessionId, String content, String entry) {
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentSession session = sessionRepository.findBySessionIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        if (session.status() != AgentSessionStatus.ACTIVE || !session.expireAt().isAfter(now())) {
            throw new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
        }
        PreparedSlots prepared = prepareLocked(session, userId, content, entry);
        persistLocked(session, userId, prepared);
        return prepared.snapshot();
    }

    /** 会话已由调用方加锁；这里只计算候选快照，不提前改写会话。 */
    PreparedSlots prepareLocked(AgentSession session, long userId, String content, String entry) {
        PersistedSlots current = slotRepository.findBySessionIdAndUserId(session.sessionId(), userId)
                .map(this::read).orElse(PersistedSlots.empty());
        String requestedSlot = latestQuestionSlot(session, userId);
        PersistedSlots next = accepted(current, requestedSlot, content);
        Map<String, String> values = new LinkedHashMap<>(next.values());
        if (entry != null && !entry.isBlank()) {
            values.put("context.entry", entry);
        }
        return new PreparedSlots(new SlotSnapshot(next.version(), values), !next.equals(current), write(next));
    }

    /** 与 run 占用共用同一事务；失败时槽位更新随事务一起回滚。 */
    void persistLocked(AgentSession session, long userId, PreparedSlots prepared) {
        if (session.activeRunId() != null) {
            throw new BusinessException(AgentErrorCode.ACTIVE_RUN_CONFLICT);
        }
        if (prepared.changed()
                && !slotRepository.update(session.id(), userId, session.version(), prepared.persistedJson())) {
            throw new IllegalStateException("会话槽位已被并发更新");
        }
    }

    private String latestQuestionSlot(AgentSession session, long userId) {
        return messageRepository.findBySessionIdAndUserId(session.id(), userId, 1).stream()
                .findFirst().filter(message -> message.type().name().equals("QUESTION"))
                .map(AgentMessage::payload).filter(java.util.Objects::nonNull).map(payload -> payload.value())
                .map(this::questionSlot).orElse(null);
    }

    private String questionSlot(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.isTextual()) {
                root = objectMapper.readTree(root.asText());
            }
            String missing = root.path("missingSlot").asText();
            return isConversationSlot(missing) ? missing : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private PersistedSlots accepted(PersistedSlots current, String slot, String value) {
        if (slot == null || value == null) {
            return current;
        }
        String normalized = normalize(slot, value);
        if (normalized == null) {
            return current;
        }
        Map<String, String> values = new LinkedHashMap<>(current.values());
        values.put(slot, normalized);
        return new PersistedSlots(current.version() + 1, values);
    }

    private String normalize(String slot, String value) {
        String text = value.trim();
        return switch (slot) {
            case "cityCode" -> CITY_CODE.matcher(text).matches() ? text : null;
            case "date" -> validDate(text) ? text : null;
            case "ticketCount" -> TICKET_COUNT.matcher(text).matches() ? text : null;
            default -> null;
        };
    }

    private boolean validDate(String text) {
        try {
            return !LocalDate.parse(text).isBefore(LocalDate.now(clock.withZone(ClockConfiguration.BUSINESS_ZONE_ID)));
        } catch (java.time.format.DateTimeParseException exception) {
            return false;
        }
    }

    private java.time.LocalDateTime now() {
        return java.time.LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    private PersistedSlots read(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            long version = root.path("version").canConvertToLong() ? root.path("version").asLong() : 0L;
            Map<String, String> values = new LinkedHashMap<>();
            for (String slot : java.util.List.of("cityCode", "date", "ticketCount")) {
                String value = root.path("values").path(slot).isTextual()
                        ? normalize(slot, root.path("values").path(slot).asText()) : null;
                if (value != null) {
                    values.put(slot, value);
                }
            }
            return new PersistedSlots(version, values);
        } catch (Exception exception) {
            return PersistedSlots.empty();
        }
    }

    private String write(PersistedSlots slots) {
        try {
            return objectMapper.writeValueAsString(Map.of("version", slots.version(), "values", slots.values()));
        } catch (Exception exception) {
            throw new IllegalStateException("会话槽位序列化失败", exception);
        }
    }

    private static boolean isConversationSlot(String value) {
        return "cityCode".equals(value) || "date".equals(value) || "ticketCount".equals(value);
    }

    private record PersistedSlots(long version, Map<String, String> values) {
        private static PersistedSlots empty() { return new PersistedSlots(0L, Map.of()); }
        private PersistedSlots { values = Map.copyOf(values); }
    }

    record PreparedSlots(SlotSnapshot snapshot, boolean changed, String persistedJson) {
    }
}
