package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
import com.miaoyu.ticket.agent.application.tool.AgentToolExecutor;
import com.miaoyu.ticket.agent.application.tool.AgentToolExecutorRegistry;
import com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** 证明未确认 Owner 字段时仍可用空 Command 做注册和 Mock 夹具，不会暴露生产白名单。 */
class AgentToolExecutorRegistryTest {

    @Test
    void shouldRegisterDeferredToolsWithoutInventingBusinessFields() {
        List<String> names = List.of(
                AgentToolDefinitions.QUERY_AVAILABLE_DATES,
                AgentToolDefinitions.QUERY_SHOWS,
                AgentToolDefinitions.QUERY_SEATS);
        List<ToolDefinition> definitions = names.stream()
                .map(AgentToolDefinitions::deferredReadOnly)
                .toList();
        ToolRegistry registry = new ToolRegistry(definitions);
        List<AgentToolExecutor<?, ?>> executors = new ArrayList<>();
        for (String name : names) {
            executors.add(executor(name, registry.find(name).orElseThrow()));
        }

        AgentToolExecutorRegistry executorRegistry = new AgentToolExecutorRegistry(registry, executors);

        assertThat(executorRegistry.require(AgentToolDefinitions.QUERY_SHOWS).definition().inputs()).isEmpty();
        assertThat(executorRegistry.require(AgentToolDefinitions.QUERY_SEATS).targetName())
                .isEqualTo(AgentToolDefinitions.QUERY_SEATS);
    }

    @Test
    void shouldRejectExecutorWhoseDefinitionIsNotInWhitelist() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.deferredReadOnly(
                AgentToolDefinitions.QUERY_SHOWS)));
        AgentToolExecutor<?, ?> executor = executor(
                AgentToolDefinitions.QUERY_AVAILABLE_DATES,
                AgentToolDefinitions.deferredReadOnly(AgentToolDefinitions.QUERY_AVAILABLE_DATES));

        List<AgentToolExecutor<?, ?>> executors = List.of(executor);
        assertThatThrownBy(() -> new AgentToolExecutorRegistry(registry, executors))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未进入白名单");
    }

    private static AgentToolExecutor<?, ?> executor(String name, ToolDefinition definition) {
        AgentToolExecutor<?, ?> executor = mock(AgentToolExecutor.class);
        when(executor.targetName()).thenReturn(name);
        when(executor.definition()).thenReturn(definition);
        when(executor.execute(org.mockito.ArgumentMatchers.any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenReturn(new ReadOnlyToolExecutionAdapter.ExecutionResult(
                        mock(ExecutionRunState.class), mock(ToolResult.class)));
        return executor;
    }
}
