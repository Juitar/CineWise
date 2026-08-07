# Agent Confirmed Order Tool Specification

## ADDED Requirements

### Requirement: 公开确认建单工具

系统 SHALL 提供 `com.miaoyu.ticket.order.api.CreateOrderTool.execute(ToolContext, CreateOrderForAgentCommand)`。Command SHALL 仅包含标准 UUID `actionId`、正十进制字符串 `showId`、正十进制字符串列表 `seatIds`；用户、金额、订单状态和幂等请求标识 SHALL 不从 Command 读取。

#### Scenario: 已确认动作创建订单

- GIVEN B 已完成 `actionId` 的归属、参数哈希、版本、过期和一次性消费校验
- AND `ToolContext` 携带当前运行的非空且最多 64 字符 `clientRequestId` 和 `idempotencyKey`
- WHEN A 执行建单工具
- THEN A 从认证上下文取得当前用户并复用原子建单 Application Service
- AND 服务端重新校验场次、座位和金额后返回 `ToolStatus.SUCCESS` 与类型化 `AgentOrderResult`
- AND `actionId` 不写入订单、订单座位或其他票务表

#### Scenario: 参数或业务失败

- GIVEN Command 的 ID、座位数量、重复座位或 Tool target 不合法，或者场次/座位业务校验失败
- WHEN A 执行建单工具
- THEN 返回 `ToolStatus.FAILED` 和稳定错误码
- AND `retryable=false`
- AND 不自动重新发起第二次建单

#### Scenario: 建单结果未知

- GIVEN 建单事务执行过程中发生无法确认结果的运行时或基础设施异常
- WHEN A 返回工具结果
- THEN 返回 `ToolStatus.PROCESSING`
- AND `retryable=false`、`suggestedNextAction=QUERY_ORIGINAL_ORDER`
- AND B 不得自动重试原建单写操作

### Requirement: 原请求结果恢复

系统 SHALL 提供按当前认证用户和 `ToolContext.clientRequestId` 查询原建单结果的公开方法。查询 SHALL 复用 A 现有幂等查询，不接受调用方提供的 `userId`，不存在时返回 `ORDER_NOT_FOUND`。

#### Scenario: 查询成功恢复

- GIVEN 原建单已提交且订单属于当前认证用户
- WHEN B 以同一 `clientRequestId` 查询
- THEN 返回 `ToolStatus.SUCCESS` 与同一 `AgentOrderResult`
- AND 不创建第二个订单、不重新锁定座位

#### Scenario: 原请求不存在

- GIVEN `clientRequestId` 合法但没有当前用户的已提交订单
- WHEN B 查询原建单结果
- THEN 返回 `ToolStatus.FAILED`、错误码 `205001` 和 `retryable=false`
