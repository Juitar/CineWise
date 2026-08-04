## ADDED Requirements

### Requirement: 已登录用户可以通过 POST SSE 提交本人会话的最小只读消息
系统 SHALL 提供 `POST /api/v1/agent/sessions/{sessionId}/messages/stream`。请求体 MUST 包含 UUID 格式的 `clientRequestId`、长度受限的 `content` 和服务端已定义的只读验证上下文；Controller MUST 只调用 Application Service，且当前用户 MUST 只从 `CurrentUserAccessor` 获取。未认证请求 MUST 保持 C 的 `401 / 201006 / SESSION_INVALID` 响应，不创建会话、运行、消息或事件。

#### Scenario: 已登录用户提交本人会话
- **WHEN** 当前用户为本人会话提交合法的只读消息
- **THEN** 系统创建或复用已有运行，并以 `text/event-stream` 返回该运行已持久化的 SSE 事件
- **AND** 响应不接受或返回请求体中的 `userId`、订单、座位、金额或认证信息

#### Scenario: 未认证用户提交消息
- **WHEN** 没有有效登录态的请求访问消息流接口
- **THEN** C 的认证边界返回 HTTP 401 和错误码 201006
- **AND** B 的 Agent 应用服务不创建任何运行或事件

### Requirement: 重复消息提交不得再次执行最小只读工具
系统 SHALL 复用已有的 `userId + sessionId + clientRequestId + requestHash` 幂等规则。相同摘要的重复 POST MUST 返回原 runId 并仅重放已保存事件；同一 `clientRequestId` 摘要不同 MUST 返回 `409 / 206009`；会话存在其他 `RUNNING` 运行时 MUST 返回 `409 / 206008`。

#### Scenario: 网络重试重放原运行
- **WHEN** 当前用户以相同 `clientRequestId` 和相同消息摘要再次提交本人会话
- **THEN** 系统返回原运行已保存的事件和状态
- **AND** `MinimalReadOnlyAgentService` 与 `rankMoviePlan` 的调用次数不增加

#### Scenario: 活动运行阻止新消息
- **WHEN** 本人会话有其他 `RUNNING` 运行且提交新的 `clientRequestId`
- **THEN** 系统返回 409 和 206008
- **AND** 不创建第二个用户消息、运行、步骤或 SSE 事件

### Requirement: SSE 只能发送固定、已持久化的公共事件
系统 SHALL 使用 `id: <eventId>`、`event: <eventType>` 和 JSON `data` 发送事件。JSON MUST 包含十进制字符串 `eventId`、`sessionId`、`runId`、可空 `planVersion`、可空 `nodeId`、`eventType`、`displayText` 和类型化 `payload`。`eventType` MUST 限于 `message.start`、`message.delta`、`plan.created`、`plan.replanned`、`step.start`、`step.complete`、`step.failed`、`tool.start`、`tool.result`、`card`、`message.complete`、`message.error`、`run.complete`、`stream.reset`；心跳 MUST 是无 ID 的 SSE 注释。

#### Scenario: 最小只读运行输出事件
- **WHEN** 最小只读运行保存计划、步骤和结构化回复
- **THEN** 系统按已保存事实顺序发送对应的计划、步骤、工具、卡片或完成事件
- **AND** 不发送模型原始思维、任意 HTML、JavaScript、URL、完整第三方响应、精确位置或密钥
