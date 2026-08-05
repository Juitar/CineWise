package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionWriteIdentifiers;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionValidator;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationContext;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationFailure;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 确认动作的应用用例。
 *
 * <p>生产持久化实现接入后，Claim 和结果保存分别由两个短事务包裹；本类不在任何数据库事务内调用 A。
 * A 的 Tool 调用仅走公开类型化适配器，订单事务不由本服务持有。
 */
@Service
public final class AgentConfirmationService {
    private final AgentConfirmationActionRepository repository;
    private final AgentConfirmationFactsProvider factsProvider;
    private final CreateOrderToolAdapter createOrderToolAdapter;
    private final AgentConfirmationEventPublisher eventPublisher;
    private final CurrentUserAccessor currentUserAccessor;
    private final Clock clock;
    private final AgentConfirmationActionValidator validator = new AgentConfirmationActionValidator();

    public AgentConfirmationService(
            AgentConfirmationActionRepository repository,
            AgentConfirmationFactsProvider factsProvider,
            CreateOrderToolAdapter createOrderToolAdapter,
            AgentConfirmationEventPublisher eventPublisher,
            CurrentUserAccessor currentUserAccessor,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.factsProvider = Objects.requireNonNull(factsProvider, "factsProvider 不能为空");
        this.createOrderToolAdapter = Objects.requireNonNull(createOrderToolAdapter, "createOrderToolAdapter 不能为空");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher 不能为空");
        this.currentUserAccessor = Objects.requireNonNull(currentUserAccessor, "currentUserAccessor 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /** 确认 body 只有布尔值；其余值均由服务端受控上下文生成。 */
    public AgentConfirmationResult confirm(String actionId, boolean confirmed, String traceId) {
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        AgentConfirmationAction action = findAction(actionId);
        AgentConfirmationValidationContext facts = factsProvider.load(action, currentUserId);
        AgentConfirmationValidationFailure failure = validator.validate(action, facts).orElse(null);
        if (failure != null) {
            return failValidation(action, failure, facts.now());
        }
        if (!confirmed) {
            AgentConfirmationAction rejected = action.reject(facts.now());
            return new AgentConfirmationResult(updateOrReadWinner(action, rejected), null, false);
        }
        AgentConfirmationAction claimed = action.claim(
                AgentActionWriteIdentifiers.forAction(action.actionId()), facts.now());
        AgentConfirmationAction winner = updateOrReadWinner(action, claimed);
        if (winner.status() != AgentConfirmationActionStatus.EXECUTING || winner.version() != claimed.version()) {
            return new AgentConfirmationResult(winner, null, false);
        }
        ToolResult<CreateOrderToolResult> toolResult =
                createOrderToolAdapter.execute(winner.actionId(), toolContext(winner, traceId), winner.command());
        AgentConfirmationAction completed = resultAction(winner, toolResult, now());
        return new AgentConfirmationResult(updateOrReadWinner(winner, completed), null, true);
    }

    /** 仅对结果未知动作使用原 action 写标识查询；本方法绝不调用 execute。 */
    public AgentConfirmationResult recover(String actionId, String traceId) {
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        AgentConfirmationAction action = findAction(actionId);
        if (action.userId() != currentUserId || action.status() != AgentConfirmationActionStatus.RESULT_UNKNOWN) {
            return new AgentConfirmationResult(action, null, false);
        }
        if (!now().isBefore(action.recoveryUntil())) {
            return new AgentConfirmationResult(action, null, false);
        }
        ToolResult<CreateOrderToolResult> queryResult =
                createOrderToolAdapter.queryByOriginalIdentifiers(toolContext(action, traceId), action.command());
        if (queryResult.status() == ToolStatus.PROCESSING) {
            return new AgentConfirmationResult(action, null, false);
        }
        AgentConfirmationAction completed = resultAction(action, queryResult, now());
        return new AgentConfirmationResult(updateOrReadWinner(action, completed), null, false);
    }

    /** SSE 回放和运行查询只刷新本人的 action 事实，绝不进入写工具。 */
    public Optional<AgentConfirmationAction> refreshForCurrentUser(String actionId) {
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        AgentConfirmationAction action = repository.findByActionId(actionId).orElse(null);
        if (action == null || action.userId() != currentUserId
                || action.status() != AgentConfirmationActionStatus.PENDING_CONFIRMATION) {
            return Optional.ofNullable(action).filter(current -> current.userId() == currentUserId);
        }
        AgentConfirmationValidationContext facts = factsProvider.load(action, currentUserId);
        AgentConfirmationValidationFailure failure = validator.validate(action, facts).orElse(null);
        return Optional.of(failure == null ? action : failValidation(action, failure, facts.now()).action());
    }

    private AgentConfirmationResult failValidation(
            AgentConfirmationAction action,
            AgentConfirmationValidationFailure failure,
            LocalDateTime now) {
        AgentConfirmationAction next = switch (failure) {
            case EXPIRED -> action.expire(now);
            case NOT_OWNER, ACTION_NOT_CONFIRMABLE -> action;
            default -> action.invalidate("确认条件已变化", now);
        };
        AgentConfirmationAction returned = next == action ? action : updateOrReadWinner(action, next);
        return new AgentConfirmationResult(returned, failure, false);
    }

    private AgentConfirmationAction resultAction(
            AgentConfirmationAction action,
            ToolResult<CreateOrderToolResult> toolResult,
            LocalDateTime now) {
        return switch (toolResult.status()) {
            case SUCCESS -> action.markSucceeded(toolResult.data().orderReference(), now);
            case FAILED -> action.markFailed("创建订单失败", now);
            case PROCESSING -> action.markResultUnknown("结果确认中", now);
        };
    }

    private AgentConfirmationAction updateOrReadWinner(
            AgentConfirmationAction current,
            AgentConfirmationAction next) {
        if (repository.compareAndSet(current.actionId(), current.version(), current.status(), next)) {
            eventPublisher.publish(next);
            return next;
        }
        return repository.findByActionId(current.actionId()).orElseThrow(
                () -> new IllegalStateException("确认动作在并发更新后不存在"));
    }

    private AgentConfirmationAction findAction(String actionId) {
        return repository.findByActionId(actionId).orElseThrow(() -> new BusinessException(
                AgentErrorCode.AGENT_RESOURCE_NOT_FOUND, "操作不可用或已失效"));
    }

    private ToolContext toolContext(AgentConfirmationAction action, String traceId) {
        return new ToolContext(
                action.runId(),
                action.nodeId(),
                action.command().toolName(),
                List.of(),
                3_000L,
                requireTraceId(traceId),
                action.writeIdentifiers().clientRequestId(),
                action.writeIdentifiers().idempotencyKey(),
                action.version());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不能为空");
        }
        return traceId;
    }
}
