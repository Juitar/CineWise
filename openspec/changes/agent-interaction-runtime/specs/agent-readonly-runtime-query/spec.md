## ADDED Requirements

### Requirement: 当前用户可以查询可恢复的只读运行详情
系统 SHALL 提供 `GET /api/v1/agent/runs/{runId}`，返回当前用户自己的运行 ID、会话 ID、状态、计划 ID/版本、开始/结束时间、十进制字符串 `lastEventId`、消息摘要和步骤摘要。步骤摘要 MUST 使用已保存的节点 ID、类型、状态、尝试次数、自动跳过和安全恢复提示，不得返回持久化实体或任意 JSON 字段。

#### Scenario: 用户查询自己的运行
- **WHEN** 当前用户查询自己已保存的运行
- **THEN** 系统返回可供 C 重建工作区的稳定 DTO
- **AND** `RUNNING` 运行保留运行中状态，`COMPLETED`、`FAILED`、`CANCELLED` 返回各自已保存终态

#### Scenario: 用户查询其他人的运行
- **WHEN** 当前用户查询不属于自己的 runId
- **THEN** 系统返回现有的 Agent 资源不存在错误
- **AND** 不返回会话、消息、步骤、事件数量或任何轨迹摘要

### Requirement: 运行详情用于恢复而不得触发副作用
系统 SHALL 将运行详情查询实现为只读操作。查询、SSE 重连和 `stream.reset` 后的投影重建 MUST NOT 创建新事件、推进步骤、调用模型或调用 D 的 `rankMoviePlan`。

#### Scenario: 断线后查询运行
- **WHEN** SSE 连接断开后当前用户查询运行详情
- **THEN** 系统返回已经持久化的状态、消息和步骤摘要
- **AND** 工具调用次数和运行版本不因查询增加

### Requirement: 运行详情的最后事件游标必须来自已提交事件水位线
系统 SHALL 以当前用户有权读取的该 runId 的最大已提交 `agent_event.event_id` 作为 `lastEventId`；没有已提交事件时 MUST 返回十进制字符串 `"0"`。查询 MUST 走 `idx_agent_event_run_event(run_id, event_id)`，不得从消息表推导续传游标。会话级 `agent_event_stream_cursor.last_committed_event_id` 只用于 SSE 重置水位线，不得误作单个 runId 的最后事件。

#### Scenario: 运行结果保存但事件尚未提交
- **WHEN** 运行事实和事件仍在同一未提交短事务中
- **THEN** 并发运行详情查询只返回此前已提交的 `lastEventId`
- **AND** 事务提交后下一次查询返回新的最大事件 ID
