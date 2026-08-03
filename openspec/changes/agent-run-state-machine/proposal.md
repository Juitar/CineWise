## Why

`agent-plan-validation` 已能把候选计划校验为初始状态均为 `PENDING` 的运行计划，但还不能从中选择下一批可执行节点，也不能以统一规则记录成功、失败、跳过、重试和重规划次数。先补齐不依赖外部系统的纯 Java 状态机，后续接入真实只读工具、持久化和 SSE 时才不会各自定义状态推进规则。

## What Changes

- 新增纯 Java 的运行状态机：从已校验 `ExecutionPlan` 中找出前置条件满足的节点，并只返回可安全开始的节点。
- 新增节点状态推进和结果记录规则，覆盖 `PENDING`、`RUNNING`、`SUCCESS`、`FAILED`、`SKIPPED`；本次不进入确认等待状态。
- 前置节点失败或被跳过时，自动将全部传递下游节点标为 `SKIPPED`，并保存 `UPSTREAM_FAILED`、来源节点和自动跳过标记。
- 对只读工具的可重试失败，按节点 `RETRY_ONCE` 策略最多再执行一次；写工具仍不得进入可执行集合，也不得被状态机重试。
- 记录每次重规划计数；单次运行最多允许两次重规划请求，超过后返回明确的拒绝结果且不再修改计划。
- 为上述规则补齐纯单元测试；不增加 Spring Bean、数据库、Redis、SSE、Controller、真实 A/D 工具、认证或确认动作。

## Capabilities

### New Capabilities

- `agent-run-state-machine`: 定义已校验运行计划的可执行节点选择、节点状态推进、下游跳过、只读重试和重规划上限。

### Modified Capabilities

无。

## Impact

- 代码只会影响 `backend/src/main/java/com/miaoyu/ticket/agent/domain/**` 及对应的 Agent 单元测试；后续应用层执行器可调用该状态机，但本 change 不创建应用层入口。
- 复用既有 `ExecutionPlan`、`ExecutionPlanNode`、`PlanNodeStatus`、`FailurePolicy`、`ToolDefinition` 和 `ToolResult<T>`，不修改其对 A、D 的工具协议。
- 不新增 REST、SSE、OpenAPI、数据库表、Flyway、Redis、外部模型或跨模块调用；A、C、D 无需为本次纯 Java 规则提供接口确认。
