## ADDED Requirements

### Requirement: 可续传的 Agent 事件必须独立持久化
系统 SHALL 在 A 分配 V009 后通过 Agent 专用前向迁移创建 `agent_event`，并使用 `event_id BIGINT AUTO_INCREMENT` 作为唯一事件序列。`session_id` 与 `run_id` MUST 使用和 V008 一致的 `VARCHAR(36)` UUID 业务格式。每条事件 MUST 保存 `session_id`、`run_id`、固定事件类型、受控 `payload_json`、`expire_at` 和 `create_time`；计划版本、节点、展示文本和发生时间保存于受控载荷。事件创建后不可更新，不设置 `update_time` 或更新路径。事件保留 30 天并随所属运行清理。系统 MUST NOT 修改 V008 或把事件写入 A、C、D 的表。

#### Scenario: 运行事实和事件一起保存
- **WHEN** Agent 保存可展示的消息、步骤或运行状态变化
- **THEN** 系统在同一短事务保存对应 `agent_event` 后才允许 SSE 读取该事件
- **AND** 事件 ID 可作为该运行和会话的稳定重放游标

### Requirement: 同一会话的续传游标不得因并发事务遗漏事件
系统 SHALL 用 `agent_session.active_run_id` 的条件占用保证同一会话同一时刻只有一个运行写入事件，并用运行/步骤的 `version + status` CAS 保证同一运行状态变化只能保存一次。每次状态变化的消息、步骤、运行事实和对应事件 MUST 在同一短事务提交；SSE 读取 MUST 只返回已提交事件。连接读取到提交前缀后 MUST 可以用最后已处理的十进制 `eventId` 继续读取后续已提交事件。

#### Scenario: 读取与后续事件事务交错
- **WHEN** 同一会话的 SSE 读取发生在下一批事件事务提交之前
- **THEN** 本次读取只返回已提交的连续会话事件前缀
- **AND** 客户端再次以最后已处理事件 ID 续传时返回后续事件，运行、步骤和工具均不重复执行

### Requirement: 客户端可以从同一会话的最后事件位置续传
系统 SHALL 读取 `Last-Event-ID` 十进制字符串，并仅返回当前用户、当前会话中事件 ID 更大的记录。重复连接或重复事件不改变运行、步骤、消息和工具执行状态。

#### Scenario: 正常续传
- **WHEN** 当前用户携带仍在保留期内的同会话 `Last-Event-ID` 重连
- **THEN** 系统按事件 ID 升序只返回该游标之后的已保存事件
- **AND** 不再次提交消息、创建运行或执行工具

#### Scenario: 游标不可续传
- **WHEN** 当前会话最早保留的事件 ID 已大于 `Last-Event-ID`
- **THEN** 系统只发送一个不入库的 `stream.reset` 事件，其 `eventId` 为当前会话最新已提交事件水位线
- **AND** 客户端可通过运行详情和历史消息重建展示，不得自动重发原消息或确认操作

### Requirement: 事件载荷必须受控、脱敏且有大小上限
系统 SHALL 只接受白名单事件类型和类型化事件载荷。`event_type` MUST 是公共事件枚举的非空值；`payload_json` MUST 是非空 JSON 对象，序列化后的 UTF-8 字节数 MUST 不超过 16 KiB。载荷 MUST NOT 包含模型原始思维、系统提示词、认证信息、精确位置、完整订单、完整第三方响应或异常堆栈。

#### Scenario: 载荷超限或含敏感字段
- **WHEN** 事件载荷超出 16 KiB 或违反字段白名单
- **THEN** 系统拒绝保存该事件并把运行写为安全失败结果
- **AND** SSE 不返回该事件的原始载荷

### Requirement: 事件查询和清理必须使用明确索引及过期条件
系统 SHALL 创建 `idx_agent_event_stream(session_id, event_id)`、`idx_agent_event_run_event(run_id, event_id)` 和 `idx_agent_event_expire(expire_at)`。迁移 MUST 约束 `expire_at >= create_time`。清理任务 MUST 只删除 `expire_at < 当前时间` 的事件，每日 02:30 每批最多 500 条，先删除 `agent_event`，只记录数量且不输出原始载荷。

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
