## Context

截至 `origin/dev` 的 PR #44，B 已有 `AgentMessageSubmissionService`、最小只读执行器和 V008 的 `agent_session`、`agent_run`、`agent_message`、`agent_run_step`。它们保证当前用户隔离、`clientRequestId` 幂等、一个活动运行、`PROCESSING` 保持 `RUNNING` 和陈旧运行恢复，但没有 HTTP 入口、SSE 事件记录或运行详情 DTO。

本 Change 只把 `rankMoviePlan` 最小只读运行暴露给 C 的 Agent 工作区。C 已提供 `CurrentUserAccessor` 和统一的未登录响应；A 仍负责迁移版本分配和未来写工具；D 的范围只限已登记只读推荐结果。V008 已合入，任何实现都不能修改其 SQL。

## Goals / Non-Goals

**Goals:**

- 提供 `POST /api/v1/agent/sessions/{sessionId}/messages/stream`、`GET /api/v1/agent/runs/{runId}` 两个只读运行时接口。
- 用持久化的 `agent_event` 作为 SSE 唯一游标，支持同一会话按 `Last-Event-ID` 重放，且重复提交不会再次调用只读工具。
- 提供安全、可展示的运行详情和轨迹，不泄露模型原始输出、密钥、精确位置或完整第三方响应。
- 在控制器、应用服务、持久化和测试之间保持现有分层；Controller 只做参数校验、当前用户入口和流式适配。

**Non-Goals:**

- 不新增或修改 V008，不创建 `agent_action`、`agent_tool_call`、`agent_feedback`，不实现确认、取消、反馈、写工具或支付跳转。
- 不调用 A 的订单、座位、支付、退票接口；不创建 D 的任务、刷新快照、发邮件或请求位置、路线、餐饮。
- 不实现完整重规划、并行多工具执行、Redis 上下文或 C 的前端页面。
- 不提供管理员轨迹页面；本 Change 仅提供本人运行查询，管理端权限和查询范围留给后续管理观测 Change。

## Decisions

### 1. 使用一个 B 自有的前向 `agent_event` 表，而不修改 V008

`agent_event` 使用 `event_id BIGINT AUTO_INCREMENT`、`session_id VARCHAR(36)`、`run_id VARCHAR(36)`、`event_type VARCHAR(32)`、`payload_json JSON`、`expire_at DATETIME(3)` 和 `create_time DATETIME(3)`。`session_id`、`run_id` 与 V008 的对外 UUID 格式完全一致；计划版本、节点、展示文本和发生时间属于受控 `payload_json`。`eventId` 是 `event_id` 的十进制字符串。读取时以 `session_id + event_id` 查询，再由 Agent Application Service 校验当前用户对会话和运行的归属。

事件是追加式事实，创建后不更新，所以不保留 `update_time` 或更新 Mapper；这是后端规范允许的不可变追加记录例外。迁移必须包含 `idx_agent_event_stream(session_id, event_id)`、`idx_agent_event_run_event(run_id, event_id)`、`idx_agent_event_expire(expire_at)`，以及 `event_type` 非空、`payload_json` 为非空 JSON 对象、`expire_at >= create_time` 的检查。载荷只由白名单 DTO 生成，UTF-8 编码后最大 16 KiB，禁止模型原文、认证信息、精确位置、完整订单或第三方原文。

选择前向迁移而非复用 `agent_message.id`，因为一条消息会产生多个计划、步骤和工具事件，消息 ID 不能作为稳定事件序列。迁移版本由 A 在复核完整 OpenSpec 后分配为 V009；未获版本和 SQL 审查结论前不生成、执行或推送迁移。

### 2. 把事件记录放在运行应用层，并坚持“先保存，再发送”

新增 `AgentRuntimeEventService`，由消息提交/结果落库用例在同一短事务中把可见运行事实和对应事件一起保存。最小只读路径至少产生 `message.start`、`plan.created`、`step.start`、`tool.start`、`tool.result`、`step.complete`/`step.failed`、`card` 或 `message.complete`、`run.complete`；没有对应事实时不伪造事件。`PROCESSING` 保留 `RUNNING` 并发送安全进度事件，不重调工具。

同一会话由现有 `active_run_id` 条件更新保证一次只有一个活动运行；同一运行的步骤/终态写入继续使用 `version + status` CAS。因此同一会话不会有两个事件写事务并发提交。一个状态变化的事实行和事件行同事务提交，SSE 只读取已提交事件。即使读取发生在下一批事件提交前，也只得到已提交前缀；客户端以最后已处理的 `eventId` 再次查询 `event_id > Last-Event-ID`，不会永久漏掉后续提交事件。这里保证的是同一会话的提交顺序，不把不同会话的全局自增 ID 当作业务顺序。

### 3. POST SSE 复用消息提交的幂等键和固定事件格式

请求体固定为 `clientRequestId`、`content` 和已存在的只读验证上下文；`Last-Event-ID` 通过请求头传递且为十进制字符串。首次提交先走现有消息提交用例；相同请求标识只返回既有运行快照。随后按 `event_id > Last-Event-ID` 读取本会话事件，使用 `id:`、`event:`、`data:` 写入同一固定 JSON：`eventId`、`sessionId`、`runId`、可空 `planVersion`、可空 `nodeId`、`eventType`、用户可展示 `displayText` 和类型化 `payload`。

事件名只使用已有公共枚举。心跳为 SSE 注释，不分配 `eventId`。有 `Last-Event-ID` 的连接发现该会话最早保留事件已晚于游标时，只返回 `stream.reset`；该重置事件不入库，其 `eventId` 是本次读取到的最新已提交事件水位线。客户端先用这个水位线重建运行和历史消息，再以它续传；服务端不重交原消息。相较于改成 EventSource 或 WebSocket，POST SSE 能保留 JSON 请求体、Cookie、取消和既有 C 客户端方案。

### 4. 运行详情只输出当前用户可恢复和可展示的信息

新增 `AgentRunQueryResponse`，包含运行 ID、会话 ID、状态、计划 ID/版本、开始/结束时间、`lastEventId`、消息摘要和步骤摘要。步骤只包含节点 ID、类型、状态、尝试次数、自动跳过和安全失败/恢复提示；轨迹事件只返回上述公共事件载荷。查询由 `AgentSessionQueryService` 或新的查询装配服务统一从 `CurrentUserAccessor` 取用户，其他用户一律按现有 `206001` 资源不存在处理。

这是 C 恢复 UI 所需的最小 DTO。管理端完整轨迹、模型审计字段和任意 JSON 查询没有必要，会扩大权限和脱敏风险。

### 5. 认证和跨模块边界保持不变

Controller 不解析 JWT、不接收 `userId`，所有应用用例只调用 `CurrentUserAccessor`。未认证由 C 的安全过滤器返回 `401 / 201006 / SESSION_INVALID`，B 不捕获后改写。D 仅通过现有 `RankMoviePlanTool` 被最小只读执行器调用；A 不在本 Change 中提供工具或写接口。

## Risks / Trade-offs

- [A 尚未复核完整 OpenSpec] → 本次补齐事件顺序、索引、格式、不可变性和清理语义；A 复核后才正式分配 V009 并审查 SQL。
- [现有最小执行器是同步调用，事件粒度不足] → 实现时在持久化状态转换处记录真实阶段事件；不为“流式效果”发送没有对应事实的增量文本。若无法在不扩大重构的前提下记录阶段事件，先保留结果重放和运行查询，并在任务中更新设计后再继续。
- [代理或浏览器断开连接] → 事件先落库、客户端按十进制字符串去重；断线后的同一请求只读既有运行，游标清理后走 `stream.reset`。
- [事件载荷泄露] → 事件 DTO 不接受模型原文和任意 Map；只允许消息摘要、固定步骤字段、`ToolResult` 安全摘要和白名单卡片载荷。
- [运行仍是 `RUNNING`] → 查询和 SSE 显示运行中及 `recoveryHint`，不把结果未知改成失败，也不自动调用工具。

## Migration Plan

1. A 复核完整 OpenSpec 后正式分配 V009，并复核 `agent_event` 的字段、索引、检查、30 天保留和回退方案。
2. B 在独立分支新增 Agent 专用迁移、Mapper/Repository、应用服务、Controller 和测试；不改 V008 或其他 Owner 的表。
3. 在 `cinewise_agent_it` 运行一次性 MySQL 8.4 集成验证，检查事件顺序、重放、游标清理和重复请求不二次执行。
4. C 用 B 提供的固定 JSON 夹具验证 POST SSE、重复事件忽略、`stream.reset` 和运行详情重建。
5. 回退时停止新 Controller 路由并保留已写 `agent_event` 记录；不得删除 V008 数据或通过回滚迁移修改共享环境。

## Open Questions

- A：复核完整 OpenSpec 后正式分配 V009，并审查最终 SQL；`cinewise_agent_it` 可继续用于 CI 集成测试，但不替代 A 的 MySQL 8.4 迁移验证。
- C/D：没有待确定的接口或职责。C 的 POST SSE/恢复规则和 D 的只读范围已有正式设计或既有确认；C 的夹具消费结果属于后续联调证据，不阻塞 B 编写接口。
