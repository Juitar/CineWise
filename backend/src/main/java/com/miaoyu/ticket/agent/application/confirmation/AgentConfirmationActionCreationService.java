package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionParameterHash;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.order.api.CreateOrderPrecheckCommand;
import com.miaoyu.ticket.order.api.CreateOrderTool;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 服务端计划在发出确认卡前创建 action；前端和模型不能直接调用本服务。 */
@Service
public class AgentConfirmationActionCreationService {
    private static final int CONFIRMATION_MINUTES = 10;

    private final AgentConfirmationActionRepository actionRepository;
    private final AgentRunRepository runRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentConfirmationEventPublisher eventPublisher;
    private final CreateOrderTool createOrderTool;
    private final CurrentUserAccessor currentUserAccessor;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public AgentConfirmationActionCreationService(
            AgentConfirmationActionRepository actionRepository,
            AgentRunRepository runRepository,
            AgentSessionRepository sessionRepository,
            AgentConfirmationEventPublisher eventPublisher,
            CreateOrderTool createOrderTool,
            CurrentUserAccessor currentUserAccessor,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.actionRepository = actionRepository;
        this.runRepository = runRepository;
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
        this.createOrderTool = createOrderTool;
        this.currentUserAccessor = currentUserAccessor;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 创建去重由 `agent_action` 唯一键保证；同一已校验 Command 返回同一个 action。 */
    @Transactional
    public AgentConfirmationAction create(CreateOrderConfirmationActionCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentRun run = runRepository.findByRunIdAndUserId(command.runId(), userId)
                .orElseThrow(() -> unavailable());
        if (run.status() != AgentRunStatus.RUNNING
                || !command.planId().equals(run.planId())
                || command.planVersion() != run.planVersion()
                || sessionRepository.findByIdAndUserId(run.sessionId(), userId)
                        .filter(session -> session.status() == AgentSessionStatus.ACTIVE)
                        .isEmpty()) {
            throw unavailable();
        }
        if (!CreateOrderTool.TARGET_NAME.equals(command.command().toolName())
                || !createOrderTool.validate(new CreateOrderPrecheckCommand(
                        command.command().showId(), command.command().sortedSeatIds())).executable()) {
            throw new BusinessException(AgentErrorCode.ACTION_PARAMETER_CHANGED);
        }
        AgentActionParameterHash hash = AgentActionParameterHash.from(command.command());
        AgentConfirmationAction existing = actionRepository.findByCreationKey(
                userId, run.id(), command.planId(), command.planVersion(), command.nodeId(),
                command.command().toolName(), hash.value()).orElse(null);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        LocalDateTime defaultExpiry = now.plusMinutes(CONFIRMATION_MINUTES);
        LocalDateTime expireAt = run.expireAt().isBefore(defaultExpiry) ? run.expireAt() : defaultExpiry;
        AgentConfirmationAction action = AgentConfirmationAction.pending(
                idGenerator.nextId(), UUID.randomUUID().toString(), userId, run.sessionId(), run.id(), run.runId(),
                command.planId(), command.planVersion(), command.nodeId(), command.command(), expireAt, now);
        try {
            actionRepository.insert(action);
        } catch (DuplicateKeyException exception) {
            return actionRepository.findByCreationKeyForUpdate(
                    userId, run.id(), command.planId(), command.planVersion(), command.nodeId(),
                    command.command().toolName(), hash.value()).orElseThrow(() -> exception);
        }
        eventPublisher.publish(action);
        return action;
    }

    private static BusinessException unavailable() {
        return new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND, "操作不可用或已失效");
    }
}
