# Design: agent-confirmed-order-tool

## Boundary

`com.miaoyu.ticket.order.api.CreateOrderTool` 是 A 对 B 的公开 Application Tool Adapter。它只依赖 A 的 `OrderApplicationService`、B 已冻结的 `ToolContext/ToolResult` 和类型化 DTO。B 不导入 A 的 persistence、entity、mapper 或 controller 内部实现。

## Command and result

`CreateOrderForAgentCommand` 仅包含 `actionId`、`showId`、`seatIds`。命令构造时校验标准 UUID 确认动作关联标识、正十进制业务 ID、座位数量上限和重复座位；不包含 `userId`、金额、订单状态、`clientRequestId` 或 `idempotencyKey`。

`ToolContext` 是幂等和身份上下文的唯一来源：当前用户由 A 的 `CurrentUserAccessor` 获取，`clientRequestId` 与 `idempotencyKey` 必须经过 `requireWriteRequestIdentifiers()`，并继续遵守 A 现有建单契约的非空且最多 64 字符限制。外层 `ToolResult.stateVersion` 始终回传 `ToolContext.stateVersion`，表示 B 的 Agent 槽位/状态快照版本；内层 `AgentOrderResult.stateVersion` 独立回传订单行版本。返回 `AgentOrderResult` 使用十进制字符串 ID、两位小数金额和带 `+08:00` 的时间；不暴露 Entity 或确认存储内容。

## Execution and recovery

1. Adapter 校验 `targetName=createOrder`、Command 和写请求标识。
2. 将字符串 ID 转换为 A 的 `CreateOrderCommand`，并调用唯一订单 Application Service；A 重新读取服务端场次、价格、座位状态并在事务内完成锁座和建单。
3. 成功映射为 `ToolStatus.SUCCESS`。
4. 可预期 `BusinessException` 映射为 `ToolStatus.FAILED`、稳定错误码、`retryable=false`；不暴露异常原文。
5. 未确认的运行时/基础设施异常映射为 `ToolStatus.PROCESSING`、`retryable=false`、`suggestedNextAction=QUERY_ORIGINAL_ORDER`，禁止 B 自动重试 POST。
6. 查询入口复用 `OrderApplicationService.queryByClientRequestId`，只按当前认证用户和 `ToolContext.clientRequestId` 恢复；找不到时返回 `ORDER_NOT_FOUND`。

`actionId` 由 B 在进入 Adapter 前完成 V012 的归属、参数哈希、版本、过期和一次性消费校验；A 不读取 B 的表，也不把 `actionId` 保存为订单字段。

## Compatibility and migration

本 change 只新增 Java 公开类型和测试，不改变已有 REST 契约、状态机、数据库结构或 Flyway 版本。`CreateOrderCommand`、`OrderView` 和既有 `by-request` REST 入口保持兼容。

## Verification

- Command 参数边界和字符串 ID 转换单测。
- Tool target、成功映射、业务失败映射、PROCESSING 禁止重试和原请求查询测试。
- 使用替换 `CurrentUserAccessor` 的订单 Application Service 测试验证用户不会来自 Command。
- Maven `verify`、OpenSpec strict 和 `git diff --check`。
