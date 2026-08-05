package com.miaoyu.ticket.agent.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationActionRepository;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionParameterHash;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionWriteIdentifiers;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** `agent_action` 的 MyBatis 持久化实现；状态推进只允许带版本和旧状态的 CAS。 */
@Repository
public class MybatisAgentConfirmationActionRepository implements AgentConfirmationActionRepository {
    private final AgentPersistenceMapper mapper;
    private final AgentConfirmationActionCodec commandCodec;

    public MybatisAgentConfirmationActionRepository(AgentPersistenceMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.commandCodec = new AgentConfirmationActionCodec(objectMapper);
    }

    @Override
    public Optional<AgentConfirmationAction> findByActionId(String actionId) {
        return Optional.ofNullable(mapper.findActionByActionId(actionId)).map(this::toDomain);
    }

    @Override
    public void insert(AgentConfirmationAction action) {
        if (mapper.insertAction(toEntity(action)) != 1) {
            throw new IllegalStateException("确认动作写入行数异常");
        }
    }

    @Override
    public boolean compareAndSet(
            String actionId,
            long expectedVersion,
            AgentConfirmationActionStatus expectedStatus,
            AgentConfirmationAction next) {
        if (!actionId.equals(next.actionId())) {
            throw new IllegalArgumentException("CAS actionId 不一致");
        }
        return mapper.updateActionWithCas(toEntity(next), expectedVersion, expectedStatus.name()) == 1;
    }

    private AgentConfirmationAction toDomain(AgentConfirmationActionEntity row) {
        AgentActionWriteIdentifiers writeIdentifiers = row.clientRequestId() == null
                ? null : new AgentActionWriteIdentifiers(row.clientRequestId(), row.idempotencyKey());
        return new AgentConfirmationAction(
                row.id(), row.actionId(), row.userId(), row.agentSessionId(), row.agentRunId(), row.runId(),
                row.planId(), row.planVersion(), row.nodeId(), commandCodec.read(row.commandSnapshot()),
                new AgentActionParameterHash(row.parameterHashVersion(), row.parameterHash()), row.expireAt(),
                AgentConfirmationActionStatus.valueOf(row.status()), writeIdentifiers, row.resultReference(),
                row.recoveryHint(), row.resultUnknownAt(), row.recoveryUntil(), row.version(), row.createTime(),
                row.updateTime());
    }

    private AgentConfirmationActionEntity toEntity(AgentConfirmationAction action) {
        return new AgentConfirmationActionEntity(
                action.id(), action.actionId(), action.userId(), action.agentSessionId(), action.agentRunId(),
                action.runId(), action.planId(), action.planVersion(), action.nodeId(), action.command().toolName(),
                commandCodec.write(action.command()), action.parameterHash().version(), action.parameterHash().value(),
                action.expireAt(), action.status().name(), action.writeIdentifiers() == null ? null
                        : action.writeIdentifiers().clientRequestId(), action.writeIdentifiers() == null ? null
                        : action.writeIdentifiers().idempotencyKey(), action.resultReference(), action.recoveryHint(),
                action.resultUnknownAt(), action.recoveryUntil(), action.version(), action.createTime(),
                action.updateTime());
    }
}
