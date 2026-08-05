## Purpose

定义 Agent 可续传事件、会话游标、用户本人运行轨迹查询、过期清理、并发提交顺序和载荷脱敏的可验证行为。

## Requirements

### Requirement: 可续传的 Agent 事件必须独立持久化
系统 SHALL 在 A 分配 V009 后通过 Agent 专用前向迁移创建 `agent_event` 和 `agent_event_stream_cursor`。`agent_event` 使用 `event_id BIGINT AUTO_INCREMENT` 作为唯一事件序列，`session_id` 与 `run_id` MUST 使用和 V008 一致的 `VARCHAR(36)` UUID 业务格式。每条事件 MUST 保存 `session_id`、`run_id`、固定事件类型、受控 `payload_json`、`expire_at` 和 `create_time`；计划版本、节点、展示文本和发生时间保存于受控载荷。事件创建后不可更新，不设置 `update_time` 或更新路径。每个会话 MUST 有一条可更新的游标记录，保存已提交最高事件 ID、可空最早保留事件 ID、版本与会话到期时间。事件保留 30 天并随所属运行清理，游标记录保留到会话可清理。系统 MUST NOT 修改 V008 或把事件写入 A、C、D 的表。

#### Scenario: 运行事实和事件一起保存
- **WHEN** Agent 保存可展示的消息、步骤或运行状态变化
- **THEN** 系统在同一短事务保存对应 `agent_event` 后才允许 SSE 读取该事件
- **AND** 事件 ID 可作为该运行和会话的稳定重放游标

### Requirement: 同一会话的续传游标不得因并发事务遗漏事件
系统 SHALL 在分配同会话事件 ID 前，以 `SELECT ... FOR UPDATE` 先锁定 V008 的会话行，再锁定或创建该会话的游标行；清理同会话事件也 MUST 使用相同锁顺序。`active_run_id` 和运行/步骤的 `version + status` CAS 只负责业务状态，不得单独作为事件提交顺序证明。每次状态变化的消息、步骤、运行事实、事件和游标水位线 MUST 在同一短事务提交；SSE 读取 MUST 只返回已提交事件。连接读取到提交前缀后 MUST 可以用最后已处理的十进制 `eventId` 继续读取后续已提交事件。

#### Scenario: 读取与后续事件事务交错
- **WHEN** 同一会话的 SSE 读取发生在下一批事件事务提交之前
- **THEN** 本次读取只返回已提交的连续会话事件前缀
- **AND** 客户端再次以最后已处理事件 ID 续传时返回后续事件，运行、步骤和工具均不重复执行

#### Scenario: 两个事务并发写同一会话
- **WHEN** 第一个事务已锁定会话和游标行但尚未提交，第二个事务尝试写同一会话事件
- **THEN** 第二个事务在事件 ID 分配前等待第一个事务提交或回滚
- **AND** 两个事务最终提交的同会话事件按提交顺序递增，游标水位线等于后一个提交事件 ID

### Requirement: 客户端可以从同一会话的最后事件位置续传
系统 SHALL 把缺失的 `Last-Event-ID` 请求头和 `"0"` 作为起始哨兵：直接按事件 ID 升序读取当前会话仍保留的事件，不读取游标记录、不校验游标归属、过期、跨会话或未来值；当前没有保留事件时只能返回空 SSE 流或心跳，不得发送 `stream.reset`。只有正整数字符串才是续传游标，系统 MUST 根据当前会话的游标记录和保留事件行校验归属。只有仍保留且属于当前会话的正整数游标才返回更大事件 ID；重复连接或重复事件不改变运行、步骤、消息和工具执行状态。

#### Scenario: 首次读取或零哨兵读取保留事件
- **WHEN** 当前用户未提供 `Last-Event-ID`，或提供 `Last-Event-ID: 0`
- **THEN** 系统按事件 ID 升序返回当前会话全部仍保留的事件
- **AND** 系统不校验该值是否属于当前会话，也不发送 `stream.reset`

#### Scenario: 首次读取或零哨兵遇到空会话事件流
- **WHEN** 当前用户未提供 `Last-Event-ID`，或提供 `Last-Event-ID: 0`，且当前会话没有保留事件
- **THEN** 系统返回空 SSE 流或心跳
- **AND** 系统不得发送 `stream.reset`，避免客户端以 `0` 重连时重复重建

#### Scenario: 正常续传
- **WHEN** 当前用户携带仍在保留期内的同会话正整数 `Last-Event-ID` 重连
- **THEN** 系统按事件 ID 升序只返回该游标之后的已保存事件
- **AND** 不再次提交消息、创建运行或执行工具

#### Scenario: 已清理游标
- **WHEN** 游标不大于当前会话已提交水位线，且小于游标记录中的最早保留 ID，或该会话已经没有保留事件
- **THEN** 系统只发送一个不入库的 `stream.reset` 事件，其 `eventId` 为当前会话最新已提交事件水位线
- **AND** 客户端可通过运行详情和历史消息重建展示，不得自动重发原消息或确认操作
- **AND** 重建完成后的下一次 `Last-Event-ID` MUST 使用该 `stream.reset` 的 `eventId`（等于 `payload.watermark`），不得使用单次运行详情的 `lastEventId`

#### Scenario: 跨会话或正常空洞游标
- **WHEN** 游标对应的保留事件属于另一会话，或位于当前会话最早/最高保留区间内但没有对应事件
- **THEN** 系统不返回另一会话事件、数量或原因，只发送当前会话水位线的 `stream.reset`
- **AND** 全局 `AUTO_INCREMENT` 的正常空洞不得被当作当前会话已清理证据

#### Scenario: 未来游标
- **WHEN** 游标大于当前会话游标记录的最高已提交事件 ID
- **THEN** 系统不执行事件查询，只发送当前会话水位线的 `stream.reset`
- **AND** 客户端必须先重建投影，再以该 `stream.reset` 的会话水位线续传

### Requirement: 事件载荷必须受控、脱敏且有大小上限
系统 SHALL 只接受白名单事件类型和类型化事件载荷。持久化 `event_type` MUST 是公共枚举中除 `stream.reset` 外的非空值，并由数据库 CHECK 白名单约束；心跳和 `stream.reset` MUST NOT 写入 `agent_event`。`payload_json` MUST 是非空 JSON 对象，序列化后的 UTF-8 字节数 MUST 不超过 16 KiB。载荷 MUST NOT 包含模型原始思维、系统提示词、认证信息、精确位置、完整订单、完整第三方响应或异常堆栈。

#### Scenario: 载荷超限或含敏感字段
- **WHEN** 事件载荷超出 16 KiB 或违反字段白名单
- **THEN** 系统回滚本次消息、步骤、运行推进和事件的原事实事务
- **AND** 系统在独立的 `version + status` CAS 安全失败事务中最多一次写入固定安全错误，SSE 不返回原始载荷

### Requirement: 事件查询和清理必须使用明确索引及过期条件
系统 SHALL 创建 `idx_agent_event_stream(session_id, event_id)`、`idx_agent_event_run_event(run_id, event_id)` 和 `idx_agent_event_expire(expire_at, event_id)`。迁移 MUST 约束 `expire_at >= create_time`。每条事件的 `expire_at` MUST 原样等于所属运行的统一到期时间；游标记录的到期时间 MUST 等于会话的最大运行到期时间。清理任务 MUST 每日 02:30 按 `expire_at ASC, event_id ASC` 每批最多 500 条处理，只清理终态运行的子记录，并先锁会话/游标、删除 `agent_event`、更新最早保留游标。当前 V008/V009 实际删除顺序固定为 `agent_event → agent_run_step → agent_message → agent_run → agent_event_stream_cursor → 无活动 agent_session`；后续 `agent_tool_call`、`agent_feedback`、`agent_action` 表引入后必须分别插入到 `agent_event` 之后、`agent_run_step` 之后、`agent_message` 之后。运行中的会话或运行不得清理。清理只记录数量且不输出原始载荷。

#### Scenario: 查询运行轨迹
- **WHEN** 当前用户查询自己的 runId 或 SSE 按会话续传
- **THEN** 系统分别使用 `run_id + event_id` 或 `session_id + event_id` 有序读取事件
- **AND** 其他用户无法通过索引查询到事件或事件数量

### Requirement: 轨迹必须可展示且完成脱敏
系统 SHALL 从运行、步骤、消息和事件生成轨迹摘要。普通用户只能读取本人运行；摘要 MUST 包含计划版本、节点状态、尝试次数、安全错误/恢复提示和事件时间，不得包含模型原始思维、认证秘密、精确位置、完整订单、完整第三方响应或未校验业务事实。

#### Scenario: 只读工具失败显示安全轨迹
- **WHEN** 最小只读工具失败、超时或返回 `PROCESSING`
- **THEN** 轨迹显示稳定错误或运行中提示及已完成步骤
- **AND** 系统不暴露异常堆栈、工具原始入参或 D 的完整响应
