package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** V008 四表领域模型的纯单元测试，不启动 Spring 或连接数据库。 */
class AgentPersistenceModelTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);
    private static final LocalDateTime EXPIRES_AT = NOW.plusDays(30);
    private static final AgentStoredJson EMPTY_JSON_ARRAY = new AgentStoredJson("[]");
    private static final AgentStoredJson EMPTY_JSON_OBJECT = new AgentStoredJson("{}");

    @Test
    void shouldRejectClearedSessionThatStillReferencesAnActiveRun() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AgentSession(
                        1L,
                        "session-1",
                        9L,
                        null,
                        AgentSessionStatus.CLEARED,
                        2L,
                        0L,
                        NOW,
                        NOW,
                        EXPIRES_AT));
    }

    @Test
    void shouldRequireFinishedAtForTerminalRunAndKeepV1HashLowercase() {
        assertThrows(
                IllegalArgumentException.class,
                () -> run(AgentRunStatus.COMPLETED, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AgentRequestHash("v1", "A".repeat(64)));
        assertDoesNotThrow(() -> run(AgentRunStatus.COMPLETED, NOW.plusSeconds(1)));
    }

    @Test
    void shouldAllowTextOnlyForUserMessages() {
        assertThrows(
                IllegalArgumentException.class,
                () -> message(AgentMessageRole.USER, AgentMessageType.PLAN_CARD));
        assertDoesNotThrow(() -> message(AgentMessageRole.ASSISTANT, AgentMessageType.PLAN_CARD));
    }

    @Test
    void shouldApplyV015ConfirmationStepStateRules() {
        assertDoesNotThrow(
                () -> step(PlanNodeType.CONFIRM_ACTION, PlanNodeStatus.WAITING_CONFIRMATION, false, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> step(PlanNodeType.COMPUTE, PlanNodeStatus.WAITING_CONFIRMATION, false, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> step(PlanNodeType.COMPUTE, PlanNodeStatus.RUNNING, true, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> step(
                        PlanNodeType.COMPUTE,
                        PlanNodeStatus.SKIPPED,
                        false,
                        "UPSTREAM_FAILED",
                        "failed-node"));
        assertDoesNotThrow(() -> step(
                PlanNodeType.COMPUTE,
                PlanNodeStatus.SKIPPED,
                true,
                "UPSTREAM_FAILED",
                "failed-node"));
    }

    private static AgentRun run(AgentRunStatus status, LocalDateTime finishedAt) {
        return new AgentRun(
                2L,
                "run-1",
                1L,
                9L,
                "request-1",
                new AgentRequestHash("v1", "a".repeat(64)),
                null,
                null,
                status,
                "trace-1",
                NOW,
                finishedAt,
                0L,
                NOW,
                NOW,
                EXPIRES_AT);
    }

    private static AgentMessage message(AgentMessageRole role, AgentMessageType type) {
        return new AgentMessage(
                3L,
                "message-1",
                1L,
                2L,
                9L,
                role,
                type,
                "展示文本",
                null,
                AgentMessageStatus.COMPLETED,
                NOW,
                NOW,
                EXPIRES_AT);
    }

    private static AgentRunStep step(
            PlanNodeType nodeType,
            PlanNodeStatus status,
            boolean autoSkipped,
            String skipReason,
            String skipSourceNodeId) {
        LocalDateTime startedAt = status == PlanNodeStatus.RUNNING ? NOW : null;
        LocalDateTime finishedAt = status == PlanNodeStatus.SKIPPED ? NOW.plusSeconds(1) : null;
        int attemptCount = status == PlanNodeStatus.RUNNING ? 1 : 0;
        return new AgentRunStep(
                4L,
                2L,
                1,
                "node-1",
                nodeType,
                EMPTY_JSON_ARRAY,
                EMPTY_JSON_ARRAY,
                status,
                FailurePolicy.FAIL,
                attemptCount,
                0,
                false,
                autoSkipped,
                skipReason,
                skipSourceNodeId,
                EMPTY_JSON_OBJECT,
                startedAt,
                finishedAt,
                0L,
                NOW,
                NOW,
                EXPIRES_AT);
    }
}
