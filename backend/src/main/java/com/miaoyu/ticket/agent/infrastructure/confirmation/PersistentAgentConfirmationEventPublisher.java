package com.miaoyu.ticket.agent.infrastructure.confirmation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationEventPublisher;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationCardStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 将已经提交的确认事实投影为可回放 SSE 卡片，不下发 command、写键或订单结果。 */
@Component
public class PersistentAgentConfirmationEventPublisher implements AgentConfirmationEventPublisher {
    private static final String DISPLAY_TITLE = "确认建单";
    private static final List<String> DISPLAY_LINES = List.of("请确认所选场次和座位");

    private final AgentSessionRepository sessionRepository;
    private final AgentRunRepository runRepository;
    private final AgentRuntimeEventService runtimeEventService;
    private final ObjectMapper objectMapper;

    public PersistentAgentConfirmationEventPublisher(
            AgentSessionRepository sessionRepository,
            AgentRunRepository runRepository,
            AgentRuntimeEventService runtimeEventService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.runRepository = runRepository;
        this.runtimeEventService = runtimeEventService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(AgentConfirmationAction action) {
        AgentRun run = runRepository.findByRunIdAndUserId(action.runId(), action.userId()).orElse(null);
        if (run == null || run.id() != action.agentRunId()) {
            return;
        }
        AgentSession session = sessionRepository.findByIdAndUserId(action.agentSessionId(), action.userId())
                .orElse(null);
        if (session == null) {
            return;
        }
        runtimeEventService.append(session, run, AgentEventType.CARD, new AgentStoredJson(payload(action)));
    }

    private String payload(AgentConfirmationAction action) {
        String dataAt = action.updateTime().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime().toString();
        Map<String, Object> payload = Map.ofEntries(
                Map.entry("type", "PLAN_CARD"),
                Map.entry("actionId", action.actionId()),
                Map.entry("actionType", "CREATE_ORDER"),
                Map.entry("expireAt", action.expireAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime()
                        .toString()),
                Map.entry("status", AgentConfirmationCardStatus.fromActionStatus(action.status()).name()),
                Map.entry("title", DISPLAY_TITLE),
                Map.entry("displayLines", DISPLAY_LINES),
                Map.entry("plans", List.of()),
                Map.entry("source", "agent_confirmation"),
                Map.entry("dataAt", dataAt),
                Map.entry("expiresAt", action.expireAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime()
                        .toString()),
                Map.entry("degraded", false));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("确认卡固定载荷序列化失败", exception);
        }
    }
}
