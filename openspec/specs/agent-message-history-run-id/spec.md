# agent-message-history-run-id Specification

## Purpose
TBD - created by archiving change agent-message-history-run-id. Update Purpose after archive.
## Requirements
### Requirement: 历史消息必须返回所属运行 UUID
系统 SHALL 在 `GET /api/v1/agent/sessions/{sessionId}/messages` 的每条 `records` 消息中增加非空字符串字段 `runId`。该字段必须是所属 `AgentRun` 的对外 UUID，不得是 `agent_run.id` 的内部 long；既有消息字段含义与 `total/page/size/records` 分页格式不得改变。历史消息顶层不得新增 userId、内部运行主键、actionId、订单号、幂等键、完整工具参数或其他敏感字段；既有确认卡 payload 可保留确认接口所需的 `actionId`，但不得扩充订单号、金额、内部运行主键、userId、幂等键或工具参数。

#### Scenario: C 从确认卡历史消息恢复运行
- **WHEN** C 在确认 POST 结果未知后读取本人活动会话的历史消息
- **THEN** 确认卡所属消息包含非空 UUID `runId`
- **AND** C 可用该值调用既有的 `GET /api/v1/agent/runs/{runId}` 查询真实状态
- **AND** B 不创建额外运行或 SSE，也不重发确认 POST

### Requirement: 历史消息运行映射必须限制当前用户和会话
系统 SHALL 先以当前认证用户和 `sessionId` 校验活动会话，再只映射同时属于该用户和该会话的运行 UUID。系统不得因消息的内部 `runId` 向前端暴露未校验运行；非本人或已清理会话继续返回既有的安全响应。

#### Scenario: 用户查询他人会话
- **WHEN** 当前用户请求不属于自己的 sessionId 的历史消息
- **THEN** 系统返回既有的资源不可用响应
- **AND** 不查询或返回该会话任何运行 UUID

### Requirement: 分页历史消息映射不得逐条查询运行
系统 SHALL 对当前页消息的 distinct 内部运行 ID 批量查询受当前用户和会话约束的运行 UUID，再按内部运行 ID 映射回消息。多条消息引用同一运行、单页多个运行或不同页查询时，系统不得发生运行错配或逐消息运行查询。

#### Scenario: 当前页有两个运行和三条消息
- **WHEN** 当前页包含两个属于已校验会话的运行，其中一个运行有两条消息
- **THEN** 三条消息分别返回其所属运行的对外 UUID
- **AND** 运行批量查询只按两个 distinct 内部运行 ID 执行一次

