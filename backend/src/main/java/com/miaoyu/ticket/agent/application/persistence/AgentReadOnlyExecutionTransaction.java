package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.run.MultiToolSupervisor;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorRequest;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 隔离模型与只读工具调用，避免网络等待占用任何数据库事务或会话行锁。
 *
 * <p>运行创建和结果落库分别由独立短事务处理；即使调用方未来增加事务，本边界也会先将其挂起。</p>
 */
@Service
public class AgentReadOnlyExecutionTransaction {
    private final MultiToolSupervisor multiToolSupervisor;

    public AgentReadOnlyExecutionTransaction(MultiToolSupervisor multiToolSupervisor) {
        this.multiToolSupervisor = Objects.requireNonNull(multiToolSupervisor, "多工具主控不能为空");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MultiToolSupervisorResult execute(
            MultiToolSupervisorRequest request, Consumer<String> onTextDelta) {
        return multiToolSupervisor.run(request, onTextDelta);
    }
}
