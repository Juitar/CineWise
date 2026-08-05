package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.miaoyu.ticket.agent.domain.confirmation.AgentActionParameterHash;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionWriteIdentifiers;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionType;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionValidator;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationCardView;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationCardStatus;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationContext;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationFailure;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.application.AgentErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 确认动作的纯领域规则测试，不启动 Spring 或连接数据库。 */
class AgentConfirmationActionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void shouldUseSameHashForEquivalentSortedSeats() {
        ConfirmedOrderCommand first = new ConfirmedOrderCommand("createOrder", "70001", List.of("4", "2"));
        ConfirmedOrderCommand second = new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4"));

        assertEquals(AgentActionParameterHash.from(first), AgentActionParameterHash.from(second));
    }

    @Test
    void shouldChangeHashWhenServerValidatedParametersChange() {
        ConfirmedOrderCommand original = new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4"));
        ConfirmedOrderCommand changed = new ConfirmedOrderCommand("createOrder", "70002", List.of("2", "4"));

        assertNotEquals(AgentActionParameterHash.from(original), AgentActionParameterHash.from(changed));
    }

    @Test
    void shouldRejectForgedOrDuplicateCommandInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConfirmedOrderCommand("createOrder", "show-1", List.of("2")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "2")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AgentActionParameterHash("v1", "client-provided-hash"));
    }

    @Test
    void shouldCreateStableWriteIdentifiersOnlyOncePerAction() {
        AgentActionWriteIdentifiers first = AgentActionWriteIdentifiers.forAction("action-1");
        AgentActionWriteIdentifiers repeated = AgentActionWriteIdentifiers.forAction("action-1");
        AgentActionWriteIdentifiers another = AgentActionWriteIdentifiers.forAction("action-2");

        assertEquals(first, repeated);
        assertNotEquals(first, another);
        assertTrue(first.clientRequestId().length() <= 64);
        assertTrue(first.idempotencyKey().length() <= 64);
    }

    @Test
    void shouldKeepInternalSessionRunIdsSeparateFromExternalRunId() {
        AgentConfirmationAction action = pendingAction();

        assertEquals(10L, action.agentSessionId());
        assertEquals(11L, action.agentRunId());
        assertEquals("run-1", action.runId());
    }

    @Test
    void shouldAllowOnlyOneWayConfirmationTransitions() {
        AgentConfirmationAction pending = pendingAction();
        AgentConfirmationAction claimed = pending.claim(
                AgentActionWriteIdentifiers.forAction(pending.actionId()), NOW.plusSeconds(1));
        AgentConfirmationAction unknown = claimed.markResultUnknown("结果确认中", NOW.plusSeconds(2));
        AgentConfirmationAction succeeded = unknown.markSucceeded("order-90001", NOW.plusSeconds(3));

        assertEquals(AgentConfirmationActionStatus.PENDING_CONFIRMATION, pending.status());
        assertEquals(AgentConfirmationActionStatus.EXECUTING, claimed.status());
        assertEquals(AgentConfirmationActionStatus.RESULT_UNKNOWN, unknown.status());
        assertEquals(AgentConfirmationActionStatus.SUCCEEDED, succeeded.status());
        assertEquals(3L, succeeded.version());
        assertThrows(IllegalStateException.class, () -> succeeded.markFailed("不能覆盖成功", NOW.plusSeconds(4)));
        assertThrows(IllegalStateException.class, () -> claimed.claim(
                AgentActionWriteIdentifiers.forAction("other"), NOW.plusSeconds(2)));
    }

    @Test
    void shouldRejectWithoutCreatingWriteIdentifiersAndExpireAtBoundary() {
        AgentConfirmationAction rejected = pendingAction().reject(NOW.plusSeconds(1));
        AgentConfirmationAction expired = pendingAction().expire(NOW.plusSeconds(1));

        assertEquals(AgentConfirmationActionStatus.REJECTED, rejected.status());
        assertEquals(AgentConfirmationActionStatus.EXPIRED, expired.status());
        assertEquals(null, rejected.writeIdentifiers());
        assertTrue(pendingAction().isExpiredAt(NOW.plusMinutes(5)));
        assertFalse(pendingAction().isExpiredAt(NOW.plusMinutes(4).plusSeconds(59)));
    }

    @Test
    void shouldInvalidateChangedOrEndedPlanWithoutCallingWriteTool() {
        AgentConfirmationAction invalidated = pendingAction().invalidate("计划版本已变化", NOW.plusSeconds(1));

        assertEquals(AgentConfirmationActionStatus.INVALIDATED, invalidated.status());
        assertEquals(null, invalidated.writeIdentifiers());
        assertThrows(IllegalStateException.class, () -> invalidated.claim(
                AgentActionWriteIdentifiers.forAction(invalidated.actionId()), NOW.plusSeconds(2)));
    }

    @Test
    void shouldRevalidateOwnerRunPlanNodeHashAndBusinessDataBeforeClaim() {
        AgentConfirmationAction action = pendingAction();
        AgentConfirmationActionValidator validator = new AgentConfirmationActionValidator();

        assertEquals(
                AgentConfirmationValidationFailure.NOT_OWNER,
                validator.validate(action, context(8L, action, true)).orElseThrow());
        assertEquals(AgentConfirmationValidationFailure.EXPIRED, validator.validate(action, contextAt(
                9L, action, AgentRunStatus.RUNNING, action.planId(), action.planVersion(),
                PlanNodeStatus.WAITING_CONFIRMATION, action.parameterHash(), true, NOW.plusMinutes(5))).orElseThrow());
        assertEquals(AgentConfirmationValidationFailure.RUN_ENDED, validator.validate(action, contextAt(
                9L, action, AgentRunStatus.COMPLETED, action.planId(), action.planVersion(),
                PlanNodeStatus.WAITING_CONFIRMATION, action.parameterHash(), true, NOW.plusSeconds(1))).orElseThrow());
        assertEquals(AgentConfirmationValidationFailure.PLAN_CHANGED, validator.validate(action, contextAt(
                9L, action, AgentRunStatus.RUNNING, action.planId(), 3,
                PlanNodeStatus.WAITING_CONFIRMATION, action.parameterHash(), true, NOW.plusSeconds(1))).orElseThrow());
        assertEquals(
                AgentConfirmationValidationFailure.NODE_NOT_WAITING_CONFIRMATION,
                validator.validate(action, contextAt(
                9L, action, AgentRunStatus.RUNNING, action.planId(), action.planVersion(),
                PlanNodeStatus.RUNNING, action.parameterHash(), true, NOW.plusSeconds(1))).orElseThrow());
        assertEquals(AgentConfirmationValidationFailure.BUSINESS_DATA_INVALID,
                validator.validate(action, context(9L, action, false)).orElseThrow());
        AgentConfirmationValidationContext changedHash = new AgentConfirmationValidationContext(
                9L,
                AgentRunStatus.RUNNING,
                "plan-1",
                2,
                PlanNodeStatus.WAITING_CONFIRMATION,
                AgentActionParameterHash.from(new ConfirmedOrderCommand("createOrder", "70002", List.of("2", "4"))),
                true,
                NOW.plusSeconds(1));
        assertEquals(AgentConfirmationValidationFailure.PARAMETERS_CHANGED,
                validator.validate(action, changedHash).orElseThrow());
        assertTrue(validator.validate(action, context(9L, action, true)).isEmpty());
    }

    @Test
    void shouldExposeOnlySafeConfirmationCardFields() {
        AgentConfirmationCardView view = AgentConfirmationCardView.from(
                pendingAction(), "确认建单", List.of("影片：示例影片", "座位：A1、A2"));

        assertEquals("action-1", view.actionId());
        assertEquals(AgentConfirmationActionType.CREATE_ORDER, view.actionType());
        assertEquals(AgentConfirmationCardStatus.PENDING_CONFIRMATION, view.status());
        assertEquals(6, AgentConfirmationCardView.class.getRecordComponents().length);
        assertThrows(IllegalArgumentException.class, () -> new AgentConfirmationCardView(
                "action-1",
                AgentConfirmationActionType.CREATE_ORDER,
                NOW.plusMinutes(5),
                AgentConfirmationCardStatus.PENDING_CONFIRMATION,
                "<b>确认</b>",
                List.of()));
    }

    @Test
    void shouldMapEveryInternalActionStatusToTheCConfirmedCardStatus() {
        for (AgentConfirmationActionStatus status : AgentConfirmationActionStatus.values()) {
            assertEquals(status.name(), AgentConfirmationCardStatus.fromActionStatus(status).name());
        }
        assertFalse(AgentConfirmationCardStatus.PENDING_CONFIRMATION.isReadOnly());
        assertTrue(AgentConfirmationCardStatus.RESULT_UNKNOWN.isReadOnly());
        assertTrue(AgentConfirmationCardStatus.REJECTED.isReadOnly());
    }

    @Test
    void shouldKeepConfirmedActionErrorCodesStable() {
        assertEquals(206003, AgentErrorCode.ACTION_EXPIRED.code());
        assertEquals(206004, AgentErrorCode.ACTION_PARAMETER_CHANGED.code());
        assertEquals(206006, AgentErrorCode.ACTION_CONFIRMING.code());
    }

    @Test
    void shouldKeepOriginalWriteIdentifiersAfterResultBecomesUnknown() {
        AgentConfirmationAction claimed = pendingAction().claim(
                AgentActionWriteIdentifiers.forAction("action-1"), NOW.plusSeconds(1));
        AgentConfirmationAction unknown = claimed.markResultUnknown("网络响应未知", NOW.plusSeconds(2));

        assertEquals(claimed.writeIdentifiers(), unknown.writeIdentifiers());
        assertTrue(unknown.status().requiresResultRecovery());
        assertThrows(IllegalStateException.class,
                () -> unknown.claim(AgentActionWriteIdentifiers.forAction("new-action"), NOW.plusSeconds(3)));
    }

    private static AgentConfirmationAction pendingAction() {
        return AgentConfirmationAction.pending(
                1L,
                "action-1",
                9L,
                10L,
                11L,
                "run-1",
                "plan-1",
                2,
                "confirm-order",
                new ConfirmedOrderCommand("createOrder", "70001", List.of("4", "2")),
                NOW.plusMinutes(5),
                NOW);
    }

    private static AgentConfirmationValidationContext context(
            long currentUserId,
            AgentConfirmationAction action,
            boolean businessDataValid) {
        return new AgentConfirmationValidationContext(
                currentUserId,
                AgentRunStatus.RUNNING,
                action.planId(),
                action.planVersion(),
                PlanNodeStatus.WAITING_CONFIRMATION,
                action.parameterHash(),
                businessDataValid,
                NOW.plusSeconds(1));
    }

    private static AgentConfirmationValidationContext contextAt(
            long currentUserId,
            AgentConfirmationAction action,
            AgentRunStatus runStatus,
            String planId,
            int planVersion,
            PlanNodeStatus nodeStatus,
            AgentActionParameterHash parameterHash,
            boolean businessDataValid,
            LocalDateTime now) {
        return new AgentConfirmationValidationContext(
                currentUserId,
                runStatus,
                planId,
                planVersion,
                nodeStatus,
                parameterHash,
                businessDataValid,
                now);
    }
}
