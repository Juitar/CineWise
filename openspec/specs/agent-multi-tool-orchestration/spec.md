# 多工具编排

## Purpose

定义 Agent 对服务端已确认工具的白名单、节点调度、确认等待、失败保护和安全结果投影，约束模型候选计划只能经过服务端校验后执行。

## Requirements

### Requirement: 服务端多工具白名单

系统 SHALL 仅执行 `ToolRegistry` 中由服务端登记且有类型化执行适配器的工具。模型、前端和计划文本 MUST NOT 通过 Bean 名、反射、Controller、Repository 或工具互调扩大可调用范围；工具不得生成计划或修改主会话。

#### Scenario: 未知工具

- **WHEN** 候选计划引用未登记工具
- **THEN** 系统在调用前拒绝计划，且不产生工具调用或持久化副作用

### Requirement: 已校验节点调度

系统 SHALL 只从 `PlanSchemaValidator` 通过的计划选择节点。所有依赖成功的只读节点可执行；写工具节点 MUST 保持等待确认，且必须通过已合入的确认建单入口执行。后续节点只能读取已成功节点的受控结果摘要或已校验输入引用。

#### Scenario: 多个无依赖只读节点

- **WHEN** 两个已登记只读工具节点没有依赖且输入引用均已校验
- **THEN** Supervisor 可以分别调度它们，并通过 `ExecutionPlanStateMachine` 记录每个结果

#### Scenario: 写节点未确认

- **WHEN** 已校验计划包含已登记写工具但没有有效确认动作
- **THEN** 节点不得启动，且不得调用写工具或生成新的 actionId、clientRequestId、idempotencyKey

### Requirement: 失败、跳过与重复调用保护

系统 SHALL 只对显式 `retryable=true` 且计划声明一次重试的只读失败执行一次重试；不可重试失败、最终失败和结果缺失 MUST 跳过尚未开始的下游节点。`PROCESSING`、超时、网络断开、SSE 断开和结果未知不得自动重试写工具。

#### Scenario: 只读失败传播

- **WHEN** 只读节点最终失败
- **THEN** 系统记录安全错误码和摘要，并跳过所有尚未开始的传递下游节点，不覆盖并行分支的成功结果

#### Scenario: 写结果未知

- **WHEN** 确认建单返回 `PROCESSING` 或调用结果未知
- **THEN** 系统只按原 action 和原写标识查询结果，不能因为重规划、断线或重复调用执行写工具

### Requirement: 安全工具结果投影

系统 SHALL 仅把工具状态、节点标识、稳定错误码、可展示下一步提示和经映射的业务摘要写入运行事件或回复。系统 MUST NOT 保存或下发模型原文、完整第三方响应、Token、完整订单或敏感参数。

#### Scenario: 工具失败

- **WHEN** 工具适配器报告失败
- **THEN** 事件和回复只包含安全摘要，且不包含异常消息或下游完整响应

### Requirement: 确认等待步骤持久化兼容

系统 SHALL 将等待用户确认的步骤保存为 `node_type=CONFIRM_ACTION` 与 `status=WAITING_CONFIRMATION`。等待期间 `started_at`、`finished_at` MUST 为空，`attempt_count`、`retry_count` MUST 为零。`WAITING_CONFIRMATION` MUST NOT 出现在其他节点类型；`CONFIRM_ACTION` 在用户确认、拒绝、失效或上游失败后仍可进入既有状态，因此两者不要求双向绑定。迁移后旧节点类型和旧状态 MUST 保持合法，且不得回填或改写已有步骤。

#### Scenario: 保存等待确认节点

- **WHEN** 已校验写节点到达确认节点且尚未收到用户决定
- **THEN** 系统保存 `CONFIRM_ACTION/WAITING_CONFIRMATION`，且其时间与尝试计数满足等待规则

#### Scenario: 读取 V015 前的步骤

- **WHEN** 运行包含 V015 前已保存的节点类型和状态
- **THEN** 系统保持原记录可读，不更新其状态或执行数据回填
