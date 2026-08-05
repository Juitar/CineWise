## ADDED Requirements

### Requirement: 当前用户可以创建自己的 Agent 会话
系统 SHALL 通过 `POST /api/v1/agent/sessions` 创建 `ACTIVE` 会话。系统 MUST 只从 `CurrentUserAccessor` 读取用户 ID，不接受请求中的用户 ID、角色、到期时间或活动运行 ID。成功响应 MUST 使用统一 `Result`，其 `data` 至少包含 `sessionId`、`summary`、`status`、`createdAt` 和 `updatedAt`；业务 ID MUST 为字符串，且不得暴露内部 `active_run_id`。

#### Scenario: 已登录用户创建会话
- **WHEN** 已登录用户调用创建会话接口
- **THEN** 系统为该用户保存一个 `ACTIVE` 会话且没有活动运行
- **AND** 响应只返回该会话的可展示摘要字段

### Requirement: 当前用户可以分页查询自己的活动会话
系统 SHALL 通过 `GET /api/v1/agent/sessions?page=&size=` 返回当前用户的 `ACTIVE` 会话，按最近更新时间倒序。系统 MUST 使用统一 `Result<PageResult<AgentSessionResponse>>`；页码从 1 开始，默认 `page=1`、`size=20`，`size` 最大为 100。查询 MUST 不产生事件、不推进步骤、不调用模型或工具。

#### Scenario: 查询本人会话列表
- **WHEN** 当前用户请求会话列表且拥有多个活动会话
- **THEN** 系统只返回该用户的会话和正确的总数、页码、每页大小
- **AND** 已清空会话不出现在列表中

#### Scenario: 查询会话列表时没有会话
- **WHEN** 当前用户没有活动会话
- **THEN** 系统返回成功的空 `records` 和总数 0

### Requirement: 当前用户可以分页读取自己会话的历史消息
系统 SHALL 通过 `GET /api/v1/agent/sessions/{sessionId}/messages?page=&size=` 返回当前用户活动会话中的历史消息，按消息创建顺序升序。系统 MUST 先以 `sessionId + 当前用户 ID + ACTIVE` 查询会话；找不到时 MUST 使用现有 `404 / 206005` 资源不存在语义。响应中的每条消息 MUST 只含 `messageId`、`role`、`type`、`text`、`payload`、`status`、`completedAt` 和 `createdAt`，不得输出模型原始上下文、认证信息或内部数值 ID。

#### Scenario: 查询本人会话历史
- **WHEN** 当前用户查询自己活动会话的消息
- **THEN** 系统按创建顺序返回该会话中可展示的消息分页
- **AND** 查询不写事件、不改变运行或步骤状态

#### Scenario: 查询其他用户或已清空会话的历史
- **WHEN** 当前用户使用不属于自己的或已清空的 `sessionId` 查询消息
- **THEN** 系统返回 `404 / 206005`
- **AND** 响应不泄露会话是否存在或其消息数量

### Requirement: 当前用户可以清空自己的会话
系统 SHALL 通过 `DELETE /api/v1/agent/sessions/{sessionId}` 逻辑清空当前用户的活动会话。系统 MUST 在一个事务内以当前用户和 `ACTIVE` 状态定位会话，并只在 `active_run_id` 为空时把会话设为 `CLEARED`、清除活动运行引用并将会话及其 Agent 子记录设为可清理。系统 MUST 保留已保存的运行、消息、步骤和事件事实，供清理任务按既有保留规则删除。

#### Scenario: 清空没有活动运行的本人会话
- **WHEN** 当前用户清空自己且没有活动运行的会话
- **THEN** 系统将该会话设为 `CLEARED` 并返回清空结果
- **AND** 后续会话列表和历史消息查询不再返回该会话

#### Scenario: 清空有活动运行的本人会话
- **WHEN** 当前用户清空自己的会话且该会话存在活动运行
- **THEN** 系统返回既有 `409 / 206008`
- **AND** 会话、活动运行引用、运行、消息、步骤和事件都不改变

### Requirement: 当前用户可以批量清空可清理会话
系统 SHALL 通过 `DELETE /api/v1/agent/sessions` 尝试清空当前用户全部活动会话，并返回 `clearedCount` 与 `skippedCount`。系统 MUST 跳过仍有活动运行或在本次并发操作中变为活动的会话，不得中断其他可清空会话，也不得留下悬空 `active_run_id`。

#### Scenario: 批量清空同时包含可清空和活动会话
- **WHEN** 当前用户批量清空会话且部分会话有活动运行
- **THEN** 系统清空没有活动运行的会话并跳过活动会话
- **AND** 响应准确返回 `clearedCount` 与 `skippedCount`
