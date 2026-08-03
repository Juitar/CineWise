## 1. 运行状态模型

- [x] 1.1 新增不可变运行快照与节点运行信息，保存节点状态、尝试次数、已用只读重试次数、跳过信息和重规划次数；不修改既有计划定义。（Owner：B；验证：模型构造与不可变性单元测试通过）
- [x] 1.2 新增纯 Java 状态机入口，接收既有 `ExecutionPlan` 和 `ToolRegistry`，返回可开始节点与新的运行快照；不注册 Spring Bean。（Owner：B；验证：编译通过且架构测试不引入 Web、持久化或 Spring 依赖）

## 2. 可开始节点与状态推进

- [x] 2.1 实现可开始节点选择：仅返回前置节点均成功的 `PENDING` 节点；`CALL_TOOL` 必须是已登记只读工具，确认节点、未知工具和写工具一律不返回。（Owner：B；验证：覆盖并列只读节点、依赖未完成、写工具、未知工具和确认节点的单元测试通过）
- [x] 2.2 实现受限状态推进：只允许合法的开始、成功、失败和处理中反馈；拒绝重启或覆盖终态节点，并记录尝试次数。（Owner：B；验证：覆盖正常推进、处理中不变、终态重启拒绝的单元测试通过）
- [x] 2.3 实现最终失败后的依赖图遍历，将全部未开始传递下游标为 `SKIPPED`，记录 `UPSTREAM_FAILED`、`autoSkipped` 与最初来源节点，不影响独立分支。（Owner：B；验证：覆盖两层下游、已跳过前置和独立分支继续可执行的单元测试通过）

## 3. 只读重试与重规划额度

- [x] 3.1 实现只读工具首次可重试失败的重新排队；第二次失败、不可重试失败和非 `RETRY_ONCE` 策略必须最终失败并触发下游跳过。（Owner：B；验证：覆盖首次重试、二次失败、不可重试和非只读保护的单元测试通过）
- [x] 3.2 实现运行级重规划额度：前两次请求批准并递增计数，第三次拒绝且保持快照不变；不生成计划或调用模型。（Owner：B；验证：覆盖 0→1→2→拒绝的单元测试通过）

## 4. 回归与交付检查

- [x] 4.1 补齐 Agent 状态机测试夹具，确保测试不依赖 MySQL、Redis、SSE、Controller、认证或真实 A/D 工具。（Owner：B；验证：`mvnw.cmd -Dtest=AgentRunStateMachineTest test` 通过）
- [x] 4.2 在 `backend/` 执行完整质量检查，并记录通过、失败和跳过数。（Owner：B；验证：`mvnw.cmd verify` 通过）
- [x] 4.3 严格校验 OpenSpec，检查任务仅在完成并验证后勾选，并核对 Git 改动范围。（Owner：B；验证：`openspec validate agent-run-state-machine --strict`、`git diff --check` 和 `git status --short --branch` 无异常）

## 5. PR 评审修正

- [x] 5.1 限制通用成功和失败入口仅处理非工具节点，工具节点只能由 `recordToolResult()` 推进。（Owner：B；验证：`AgentRunStateMachineTest` 覆盖工具节点直接成功、直接失败均被拒绝）
- [x] 5.2 收紧运行与节点状态的构造和替换入口，拒绝伪造终态及非法计数。（Owner：B；验证：`AgentRunStateMachineTest` 覆盖私有构造、成功节点零尝试和 `retryCount > attemptCount`）
