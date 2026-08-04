package com.miaoyu.ticket.agent.infrastructure.persistence;

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

/** B 的持久化行与领域模型转换器，不让 MyBatis 行对象离开基础设施层。 */
final class AgentPersistenceMappings {
    private AgentPersistenceMappings() {
    }

    static AgentSession toDomain(AgentSessionEntity entity) {
        return new AgentSession(
                entity.id(), entity.sessionId(), entity.userId(), entity.summary(),
                AgentSessionStatus.valueOf(entity.status()), entity.activeRunId(), entity.version(),
                entity.createTime(),
                entity.updateTime(), entity.expireAt());
    }

    static AgentSessionEntity toEntity(AgentSession session) {
        return new AgentSessionEntity(
                session.id(), session.sessionId(), session.userId(), session.summary(), session.status().name(),
                session.activeRunId(), session.version(), session.createTime(), session.updateTime(),
                session.expireAt());
    }

    static AgentRun toDomain(AgentRunEntity entity) {
        return new AgentRun(
                entity.id(), entity.runId(), entity.sessionId(), entity.userId(), entity.clientRequestId(),
                new AgentRequestHash(entity.requestHashVersion(), entity.requestHash()), entity.planId(),
                entity.planVersion(),
                AgentRunStatus.valueOf(entity.status()), entity.traceId(), entity.startedAt(), entity.finishedAt(),
                entity.version(), entity.createTime(), entity.updateTime(), entity.expireAt());
    }

    static AgentRunEntity toEntity(AgentRun run) {
        return new AgentRunEntity(
                run.id(), run.runId(), run.sessionId(), run.userId(), run.clientRequestId(),
                run.requestHash().version(),
                run.requestHash().value(), run.planId(), run.planVersion(), run.status().name(), run.traceId(),
                run.startedAt(), run.finishedAt(), run.version(), run.createTime(), run.updateTime(), run.expireAt());
    }

    static AgentMessage toDomain(AgentMessageEntity entity) {
        return new AgentMessage(
                entity.id(), entity.messageId(), entity.sessionId(), entity.runId(), entity.userId(),
                AgentMessageRole.valueOf(entity.role()), AgentMessageType.valueOf(entity.messageType()), entity.text(),
                entity.payloadJson() == null ? null : new AgentStoredJson(entity.payloadJson()),
                AgentMessageStatus.valueOf(entity.status()), entity.completedAt(), entity.createTime(),
                entity.expireAt());
    }

    static AgentMessageEntity toEntity(AgentMessage message) {
        return new AgentMessageEntity(
                message.id(), message.messageId(), message.sessionId(), message.runId(), message.userId(),
                message.role().name(), message.type().name(), message.text(),
                message.payload() == null ? null : message.payload().value(), message.status().name(),
                message.completedAt(), message.createTime(), message.expireAt());
    }

    static AgentRunStep toDomain(AgentRunStepEntity entity) {
        return new AgentRunStep(
                entity.id(), entity.runId(), entity.planVersion(), entity.nodeId(),
                PlanNodeType.valueOf(entity.nodeType()),
                new AgentStoredJson(entity.dependsOnJson()), new AgentStoredJson(entity.inputRefsJson()),
                PlanNodeStatus.valueOf(entity.status()), FailurePolicy.valueOf(entity.failurePolicy()),
                entity.attemptCount(),
                entity.retryCount(), entity.recoveryPending(), entity.autoSkipped(), entity.skipReason(),
                entity.skipSourceNodeId(), new AgentStoredJson(entity.slotSnapshotJson()), entity.startedAt(),
                entity.finishedAt(),
                entity.version(), entity.createTime(), entity.updateTime(), entity.expireAt());
    }

    static AgentRunStepEntity toEntity(AgentRunStep step) {
        return new AgentRunStepEntity(
                step.id(), step.runId(), step.planVersion(), step.nodeId(), step.nodeType().name(),
                step.dependsOn().value(), step.inputRefs().value(), step.status().name(),
                step.failurePolicy().name(),
                step.attemptCount(), step.retryCount(), step.recoveryPending(), step.autoSkipped(), step.skipReason(),
                step.skipSourceNodeId(), step.slotSnapshot().value(), step.startedAt(), step.finishedAt(),
                step.version(),
                step.createTime(), step.updateTime(), step.expireAt());
    }
}
