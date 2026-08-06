## Context

`origin/dev` 已有 `ToolRegistry`、`PlanSchemaValidator`、`ExecutionPlanStateMachine`、运行 CAS/SSE 持久化，以及确认建单的 `AgentConfirmationService`。当前 `MinimalReadOnlyAgentService` 只串行执行 `rankMoviePlan`，状态机只提供重规划计数，尚没有实际 Supervisor。`agent-card-event-payload` 位于其他 worktree，不能读取或依赖。

## Goals / Non-Goals

**Goals:**

- 让 B 的 Supervisor 以服务端白名单调度多个已确认工具，保留类型化适配器边界。
- 用已校验的引用、节点状态和安全摘要串联只读结果；写节点只能由既有确认动作推进。
- 在同一运行内有限重规划，提升计划版本并隔离旧事件、旧动作和迟到结果。
- 对运行版本更新、事件和恢复沿用既有 Agent 表与 CAS，并纳入 MySQL 8.4 CI。

**Non-Goals:**

- 不改 `agent-confirmed-order-execution` 的 action、REST、DTO、迁移、写键或恢复代码。
- 不新增 A/D 工具、不接入真实模型 Provider、不新增密钥或依赖。
- 不实现 C 卡片 payload、前端、支付、退票或管理员轨迹。

## Decisions

### 1. Supervisor 只编排，工具只执行

`MultiToolSupervisor` 依赖 `ModelGateway`、校验器、状态机和按具体类型登记的执行适配器。模型只返回 `CandidatePlan`；校验器将它变为 `ExecutionPlan`，Supervisor 才能选择节点。工具没有 Supervisor 引用，因此不能再调用工具、生成计划或更新会话。

### 2. 写节点复用既有确认建单

`rankMoviePlan` 继续由 B 的只读适配器执行。`createOrder` 只作为白名单中的写工具定义和确认门控目标：Supervisor 不能直接调用它，确认动作仍由 `AgentConfirmationActionCreationService` 创建、`AgentConfirmationService` claim 后在事务外调用 A。这样不会为重规划生成新的 actionId 或写键。

### 3. 重规划只替换未完成失败分支

Supervisor 在失败节点提供的安全失败摘要和成功节点的受控摘要基础上请求下一候选计划。它先调用状态机额度检查，再要求新计划版本高于当前版本，重新校验后合并：无依赖成功节点保持、成功写节点不可重跑，失败节点和其下游可替换或跳过。旧计划版本的结果被拒绝，防止迟到回调覆盖新计划。

### 4. 版本、事务和恢复

运行计划更新使用 `agent_run.version` 条件更新；步骤和事件带 `plan_version`。工具调用在事务外，状态/步骤/事件由短事务保存。写结果未知、超时、网络/SSE 断线均委托既有确认恢复按原 action 和原写标识查询；只读的显式可重试失败最多一次。MySQL 行锁、CAS、事件顺序和恢复只由 `Backend MySQL Integration` 的 `mysql-integration` job 验证。

V015 只替换 V008 的三条 `agent_run_step` CHECK：新增 `CONFIRM_ACTION` 节点、`WAITING_CONFIRMATION` 状态及其时间规则。`WAITING_CONFIRMATION` 必须是 `CONFIRM_ACTION`，但反向不成立：确认节点还可以处于既有 `PENDING`、`SUCCESS`、`FAILED` 或 `SKIPPED`，以表示计划创建、用户确认结果或上游跳过。等待期间 `started_at/finished_at` 都为空、`attempt_count/retry_count` 都为零；用户提交确认后才按既有终态时间规则记录一次处理。旧节点类型和旧状态仍全部合法，不做数据回填。

### 5. 卡片协议后续接入

本 change 仅发出已有安全事件和 `planVersion`。待 `agent-card-event-payload` 合入后，由 C 把其确认卡字段与本 change 的安全摘要/版本字段接入，不复制或猜测其 payload。

## Risks / Trade-offs

- [确认建单没有通用 Command 映射] → 写节点只等待既有确认入口，不将模型输入转换为 A 参数。
- [旧运行事件竞争] → 以计划版本和运行 CAS 拒绝迟到状态保存；MySQL CI 覆盖竞争。
- [模型返回不可用计划] → 使用确定性 `MockModelGateway` 和完整服务端校验，拒绝后给固定提示。
- [C 卡片协议尚未合入] → 保留最后一项集成任务，未合入前不实现前端 payload。

## Migration Plan

1. A 静态审查 `V015__extend_agent_run_step_confirmation_states.sql` 后，才安排 MySQL 8.4 验证。
2. 在 GitHub Actions `Backend MySQL Integration` / `mysql-integration` 用一次性 MySQL 8.4 和 `cinewise_agent_it` 验证空库初始化、重复初始化、旧状态兼容、CAS、重规划版本、事件顺序和结果未知恢复。
3. 发布前执行本地单元测试与 `backend/mvnw.cmd verify`。出现回归时以新的前向迁移修复，不改 V008。

## Open Questions

- C：`agent-card-event-payload` 合入后确认卡如何携带版本和安全摘要；本 change 不阻塞其余 B 实现。
- A/D：没有新的工具接口确认，因此本 change 不登记额外工具或字段。
