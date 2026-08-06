## ADDED Requirements

### Requirement: 卡片事件必须有完整外层
系统 SHALL 对每条 `eventType="card"` 输出 `eventId`、`eventType`、`sessionId`、`runId`、`occurredAt`、`planId`、`planVersion`、`nodeId`、`displayText` 和 `payload`。`eventId/sessionId/runId/eventType/displayText` 必填字符串；`occurredAt` 必填 ISO 8601 时间；`planId/planVersion/nodeId` 必须出现但可为 null，计划相关卡片必须同时给出非空 `planId` 和大于零的 `planVersion`。`payload.type` 是六类白名单值。

#### Scenario: C 收到完整推荐卡
- **WHEN** B 发送 `MOVIE_CARD` 或 `PLAN_CARD`
- **THEN** C 只读取当前事件完成结构校验和渲染
- **AND** 不查询历史消息补齐字段

### Requirement: 六类 payload 必须可校验
系统 SHALL 仅允许 `TEXT`、`QUESTION`、`MOVIE_CARD`、`PLAN_CARD`、`PROGRESS`、`ERROR`。未知 `eventType` 或 `payload.type` MUST 降级为转义后的安全文本；已知类型缺字段或字段类型错误 MUST 拒绝且不推进事件游标。

#### Scenario: 未知卡片类型
- **WHEN** C 收到未知 `payload.type`
- **THEN** C 显示安全占位文本
- **AND** 不动态加载组件、URL 或 HTML

### Requirement: QUESTION 必须表达位置授权和失效
QUESTION payload SHALL 包含 `questionId`、`questionKind`、`message`、`options`、`allowFreeText`、`input?`、`requiresConfirmation` 和 `expiresAt`。`LOCATION_PERMISSION` 必须带 `locationAuthorization.permission=DEVICE_LOCATION`、`authorizationState`、`purpose=ROUTE_PLANNING`、`resubmittable`、`deniedAction` 和 `expiredAction`。拒绝使用 `authorizationState=DENIED`，过期使用 `EXPIRED`，重新提交创建新 `questionId`；前端以 `questionKind` 判断，不解析自然语言。

#### Scenario: 用户拒绝位置授权
- **WHEN** 浏览器权限被拒绝
- **THEN** C 发送结构化结果 `DENIED` 或改用 `manualPlace` 文本输入
- **AND** 不读取位置、不发路线请求

### Requirement: 确认结果接口只传确认结果
系统 SHALL 保持 `POST /api/v1/agent/actions/{actionId}/confirm`，Path 参数 `actionId` 为服务端卡片给出的字符串；请求体严格为 `{"confirmed": true|false}`。成功或用户拒绝返回 `actionId/runId/planVersion/status/updatedAt`；过期返回 `206003`，参数或计划变化返回 `206004`，重复确认返回 `206006`。`RESULT_UNKNOWN` 时 C 显示结果确认中，只查询原 action 的 SSE 或运行快照，不自动重发确认。

#### Scenario: 重复确认
- **WHEN** 同一个 `actionId` 已处理或处理中
- **THEN** 服务端返回现有结果或 `206006`
- **AND** 前端不生成新 actionId 或再次提交
