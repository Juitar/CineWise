package com.miaoyu.ticket.agent.application.confirmation;

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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 确认动作的应用用例。
 *
 * <p>生产持久化实现接入后，Claim 和结果保存分别由两个短事务包裹；本类不在任何数据库事务内调用 A。
 * A 的正式 Tool 未确认前只与 Mock 一起用于单元测试。
 */
public final class AgentConfirmationService {
    private final AgentConfirmationActionRepository repository;
    private final AgentConfirmationFactsProvider factsProvider;
    private final CreateOrderToolAdapter createOrderToolAdapter;
    private final CurrentUserAccessor currentUserAccessor;
    private final Clock clock;
    private final AgentConfirmationActionValidator validator = new AgentConfirmationActionValidator();

    public AgentConfirmationService(
            AgentConfirmationActionRepository repository,
            AgentConfirmationFactsProvider factsProvider,
            CreateOrderToolAdapter createOrderToolAdapter,
            CurrentUserAccessor currentUserAccessor,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.factsProvider = Objects.requireNonNull(factsProvider, "factsProvider 不能为空");
        this.createOrderToolAdapter = Objects.requireNonNull(createOrderToolAdapter, "createOrderToolAdapter 不能为空");
        this.currentUserAccessor = Objects.requireNonNull(currentUserAccessor, "currentUserAccessor 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /** 确认 body 只有布尔值；其余值均由服务端受控上下文生成。 */
    public AgentConfirmationResult confirm(String actionId, boolean confirmed, String traceId) {
        AgentConfirmationAction action = repository.findByActionId(actionId).orElseThrow(
                () -> new IllegalArgumentException("确认动作不存在"));
        long currentUserId = currentUserAccessor.requireCurrentUserId();
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
                createOrderToolAdapter.execute(toolContext(winner, traceId), winner.command());
        AgentConfirmationAction completed = resultAction(winner, toolResult, now());
        return new AgentConfirmationResult(updateOrReadWinner(winner, completed), null, true);
    }

    /** 仅对结果未知动作使用原 action 写标识查询；本方法绝不调用 execute。 */
    public AgentConfirmationResult recover(String actionId, String traceId) {
        AgentConfirmationAction action = repository.findByActionId(actionId).orElseThrow(
                () -> new IllegalArgumentException("确认动作不存在"));
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        if (action.userId() != currentUserId || action.status() != AgentConfirmationActionStatus.RESULT_UNKNOWN) {
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
            return next;
        }
        return repository.findByActionId(current.actionId()).orElseThrow(
                () -> new IllegalStateException("确认动作在并发更新后不存在"));
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
