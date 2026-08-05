## ADDED Requirements

### Requirement: 确认接口只接收确认结果
系统 SHALL 提供 `POST /api/v1/agent/actions/{actionId}/confirm`，请求体 MUST 仅含布尔字段 `confirmed`。服务端 MUST 从 `CurrentUserAccessor` 获得用户，并在确认时重新校验 action、运行状态、节点、计划版本、参数摘要和业务数据；运行已结束、节点不允许确认或业务数据失效时不得调用 A。

#### Scenario: 合法用户确认
- **WHEN** action 属于当前用户、仍有效、运行和计划版本匹配且 confirmed 为 true
- **THEN** 系统先保存 action 的确认事实，再进入独立的写工具执行阶段
- **AND** 请求体中的任何额外交易字段都不会参与建单

### Requirement: A 建单调用只能使用公开类型化能力
系统 SHALL 只经 A 已确认的 `CreateOrderTool.execute(ToolContext, CreateOrderForAgentCommand)` 调用建单。Command MUST 只含 `actionId`、`showId`、`seatIds`；`ticketCount` 由 A 按最终成功锁定座位计算。B MUST NOT 调用 A Controller、Entity、Mapper、Repository、数据库表或本机 HTTP，也不得自行校验或改写库存、金额、用户归属和订单状态。

#### Scenario: A Tool 调用前授权
- **WHEN** B 已通过 CAS 将 action 推进为 `EXECUTING` 并在事务外调用 A Tool
- **THEN** A 使用 B 的公开 `AgentActionAuthorizationPort` 校验当前认证用户、runId/nodeId/toolName、计划/摘要和稳定键
- **AND** 授权失败统一由 A 映射为 `205004`，不泄露 action 是否存在

### Requirement: 写调用和结果保存必须分离事务
系统 SHALL 在短事务中完成确认 Claim，在事务外调用 A，并在新的短事务中通过 CAS 保存结果。确认事务 MUST NOT 覆盖 A 建单的网络等待时间；结果保存成功后才允许 SSE 看见成功、失败或结果未知事实。

#### Scenario: A 调用超时
- **WHEN** A 建单调用超时、网络断开、SSE 中断或响应丢失
- **THEN** 系统将 action 记录为 `RESULT_UNKNOWN` 并保留原稳定键
- **AND** 不自动重新发送建单请求

### Requirement: 写结果未知只能按原标识恢复
系统 SHALL 对一个 action 仅生成一次稳定 clientRequestId 和 idempotencyKey。恢复器或用户再次查看结果 MUST 使用 A 按当前用户和原 clientRequestId 提供的公开查询；查询没有明确成功或失败时 MUST 保持 `RESULT_UNKNOWN` 并提示结果确认中，不能生成 actionId 或新键规避未知结果。A 返回 `PROCESSING` 时 MUST 设置 `retryable=false`、`replanSuggested=false`。

#### Scenario: 断线后恢复
- **WHEN** 用户在建单后断线并通过 REST 或 SSE 重连
- **THEN** 系统只回放已保存 action 状态并按原标识查询已知结果
- **AND** 不因为重连、重规划或重复按钮点击再次建单

### Requirement: 失败路径不得产生交易副作用
系统 SHALL 在过期 `206003`、参数变化 `206004`、重复确认/处理中 `206006`、越权、action 不存在、运行结束、计划版本变化、业务失效或用户拒绝时，不调用 A、不创建新 action/键，也不修改订单、座位、支付或退票状态；REST/SSE 只返回固定、安全、可展示的信息。

#### Scenario: 业务校验失败
- **WHEN** 确认前重新校验发现相关场次或座位候选已失效
- **THEN** 系统将 action 保持或推进为安全不可执行状态并返回固定提示
- **AND** 不把过期参数发送给 A
