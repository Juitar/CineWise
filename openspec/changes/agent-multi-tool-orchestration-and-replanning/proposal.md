## Why

当前 Agent 只能同步执行单个只读推荐节点，确认建单虽已合入，但尚未由同一运行编排器管理多个工具、节点依赖和失败后的替代计划。需要在不改变确认建单实现的前提下，把服务端白名单、节点调度和有限重规划补齐。

## What Changes

- 增加多个服务端登记工具的执行注册和已校验计划调度；未知工具、未确认写调用和未经校验的输入引用一律不能执行。
- 增加 Supervisor：模型只产生候选计划，服务端完成结构、工具、依赖、输入和确认条件校验，并保存安全的执行摘要。
- 增加最多两次的重规划规则：保留无依赖成功节点和已成功写节点，替换或跳过失败分支，提升计划版本，使旧计划事件和旧确认动作失效。
- 接入已合入的 `rankMoviePlan` 与确认建单公开能力；不改确认建单 action、接口、DTO、持久化或恢复实现。

## Capabilities

### New Capabilities

- `agent-multi-tool-orchestration`: 多工具白名单、计划校验、节点调度、失败跳过与安全工具摘要。
- `agent-replanning-supervisor`: 模型候选计划的受控重规划、计划版本隔离、旧事件和旧动作失效。

### Modified Capabilities

- 无。

## Impact

- B：`agent` 的计划校验、状态机、应用层 Supervisor、工具注册、持久化运行更新和测试。
- A：仅复用已确认的 `CreateOrderTool` 与既有确认动作；不修改 A 的 DTO、错误码、订单或表。
- C：卡片 payload change 尚未合入，本 change 不复制其字段；后续由 C 根据 `planVersion`、安全摘要和 action 状态接入展示。
- D：仅复用已确认的 `RankMoviePlanTool`；不接入未确认工具。
- 数据库：A 已分配 `V015__extend_agent_run_step_confirmation_states.sql`。该前向迁移只扩展 `agent_run_step` 的节点/状态 CHECK，不修改 V008；A 静态审查后才进入 GitHub Actions 的 MySQL 8.4 验证。
