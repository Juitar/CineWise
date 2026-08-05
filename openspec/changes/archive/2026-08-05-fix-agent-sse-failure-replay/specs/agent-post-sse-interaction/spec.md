## MODIFIED Requirements

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

#### Scenario: 最小只读工具异常后重放安全失败事件
- **WHEN** 本次 POST SSE 的最小只读工具在运行执行中抛出异常，且 Agent 已提交安全失败事实
- **THEN** 当前 SSE 连接按已保存事件顺序发送 `message.error`、`run.complete` 等失败事件后正常结束
- **AND** 系统不得发送异常文本、异常堆栈、工具原始参数，且不得创建第二个运行或再次调用工具
