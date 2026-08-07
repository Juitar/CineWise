## ADDED Requirements

### Requirement: 确认卡只显示脱敏内容
系统 SHALL 只显示确认卡的标题、`displayLines`、有效期和固定状态，MUST NOT 显示 actionId、内部工具参数、订单号、金额或未声明字段。

#### Scenario: 收到待确认卡
- **WHEN** 当前用户收到合法的 `PENDING_CONFIRMATION` 卡片
- **THEN** 页面显示确认和拒绝按钮及脱敏摘要
- **AND** actionId 仅供模块内部调用接口

### Requirement: 确认写操作不得重复提交
系统 SHALL 向 `POST /api/v1/agent/actions/{actionId}/confirm` 发送仅含 `confirmed` 的请求体。提交中、执行中、结果未知及终态 MUST 禁止再次提交，断网、超时和 SSE 重连不得自动重发。

#### Scenario: 用户重复点击
- **WHEN** 第一次确认请求尚未得到明确结果
- **THEN** 第二次点击不创建新的 POST

### Requirement: 未知结果必须按历史消息 runId 恢复
系统 SHALL 在确认结果未知时重新拉取当前会话历史消息，按原 actionId 找到确认卡消息，再用该消息的 UUID `runId` 调用既有运行查询并恢复服务器状态。

#### Scenario: 确认 POST 超时
- **WHEN** POST 已发送但响应超时
- **THEN** 页面先显示结果未知并禁用操作
- **AND** 系统只执行历史消息查询和运行查询，不重发 POST

### Requirement: 权限和错误提示必须安全
系统 SHALL 对 401、403、404、409、422 和 5xx 显示固定提示。409 必须优先按业务错误码判断：206004 进入失效状态，206006 或不含 206004 的 409 进入处理中。403 与 404 不得泄露操作是否属于其他用户；失败、拒绝、过期和失效状态均不得再次提交。

#### Scenario: 操作不属于当前用户
- **WHEN** 服务端返回 403 或 404
- **THEN** 页面显示“该确认操作不可用”并禁用操作
