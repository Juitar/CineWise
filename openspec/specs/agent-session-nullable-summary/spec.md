# agent-session-nullable-summary Specification

## Purpose
TBD - created by archiving change fix-agent-session-summary-null. Update Purpose after archive.
## Requirements
### Requirement: Agent 会话响应保留空摘要字段
当 Agent 会话尚未生成摘要时，系统 MUST 在 `POST /api/v1/agent/sessions` 和会话列表响应的每条记录中输出 `summary: null`，不得因全局 JSON 非空配置省略该字段。

#### Scenario: 创建无摘要会话
- **WHEN** 当前用户创建一个尚无摘要的 Agent 会话
- **THEN** 响应 `data` 包含 `sessionId`、`summary`、`status`、`createdAt` 和 `updatedAt`，其中 `summary` 为 JSON null

#### Scenario: 会话列表包含无摘要会话
- **WHEN** 当前用户查询包含无摘要会话的列表
- **THEN** 每条无摘要会话记录包含 JSON null 的 `summary` 字段

