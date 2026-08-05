# Proposal: agent-confirmed-order-tool

## Why

B 的 `agent-confirmed-order-execution` 已完成确认动作领域模型和 V012 持久化，但 A 当前只有页面 REST 建单入口。B 无法在不跨模块访问订单内部实现的情况下执行已确认的 Agent 建单，也无法安全查询结果未知的原始请求。

## What Changes

- 在 A 的订单公开 API 包提供 `CreateOrderTool`、`CreateOrderForAgentCommand` 和类型化 `AgentOrderResult`。
- 复用 A 现有 `OrderApplicationService` 完成当前用户、金额、场次、座位、锁座、订单写入和幂等校验。
- 复用 `ToolContext` 中的 `clientRequestId`、`idempotencyKey` 和 `stateVersion`；`actionId` 只作为 B 已完成确认校验后的短暂关联参数，不写入订单表。
- 提供按当前认证用户和原 `clientRequestId` 的工具结果查询，`PROCESSING` 只允许查询原结果，不自动重试建单。
- 补充 Tool Adapter 单元测试、固定结果映射测试和 OpenSpec/代码质量验证。

## Non-Goals

- 不实现或修改 B 的确认动作存储、消费、REST、SSE 或 Agent 运行状态机。
- 不新增或修改 Flyway、订单表、座位表和现有 REST DTO。
- 不解析 JWT、Cookie 或直接访问认证模块、订单 Repository、Mapper、Entity。

## Owner

- A：订单公开 Tool Adapter、订单结果类型和票务交易测试。
- B：确认动作校验、Tool Router、REST/SSE 接线和 Agent 运行恢复消费者。
