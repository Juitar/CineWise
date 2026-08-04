## Why

Agent 已经能够在服务端保存最小只读运行、消息和步骤，但 C 还没有可调用的 Controller、POST SSE 返回、断线续传或运行详情接口。没有持久化事件，前端断线后也无法从最后事件位置继续读取，管理端无法按运行查看脱敏轨迹。

本 Change 把已合入的只读执行和四表持久化接到可联调的交互运行时，先交付只读对话的可恢复主流程；确认动作、A 的写工具和重规划留在后续 Change，避免在 A 的写接口尚未定稿时猜测参数。

## What Changes

- 新增 B 的 Agent Controller：已登录用户可向本人会话提交消息并通过 POST SSE 接收只读运行事件，也可查询本人运行详情。
- 新增 `agent_event` 前向迁移、事件持久化和按十进制 `Last-Event-ID` 的续传；事件先持久化，再对 SSE 连接可见。
- 固化同一会话的事件提交顺序、运行轨迹查询索引、V008 UUID 格式、不可变事件载荷、30 天过期与清理规则，作为 A 审查 V009 SQL 的依据。
- 新增可展示的只读运行详情和脱敏轨迹：运行状态、计划版本、步骤状态、消息摘要、事件游标和安全失败说明。
- 将现有 `AgentMessageSubmissionService` 的持久化结果映射为固定 SSE 事件；重复 `clientRequestId` 或重连只能读取已有运行和事件，不再次执行最小只读工具。
- 补齐 Agent POST SSE、权限、重复事件、游标已过期、运行中和失败结果的后端测试，并提供 C 可消费的固定 JSON 夹具。

## Capabilities

### New Capabilities

- `agent-post-sse-interaction`: 已登录用户提交只读 Agent 消息、接收固定事件并安全重连。
- `agent-event-trajectory`: 持久化可续传事件，并向用户和管理端提供脱敏运行轨迹查询。
- `agent-readonly-runtime-query`: 为 C 提供只读运行、消息和步骤的稳定查询 DTO 与结果恢复规则。

### Modified Capabilities

无。

## Impact

- 影响 B 的 `agent/api`、`agent/application`、`agent/domain`、`agent/infrastructure`，新增 V008 之后的 Agent 专用前向迁移；不得修改 V008。
- 复用 C 的 `CurrentUserAccessor` 和既有 `401 / 201006 / SESSION_INVALID`；不新增身份解析、认证端点或错误码。
- C 消费已经由前端设计固定的 POST SSE 和运行查询 DTO；本 Change 提供事件夹具，C 在后续联调中验证重连和 `stream.reset` 页面处理，本 Change 不改 C 的前端代码。
- A 先复核本 Change 的事件顺序、表结构和清理规则，再正式分配 V009 并审查 SQL；本 Change 不调用 A 的写工具、不创建 `agent_action`、不调用订单、座位、支付或退票能力。
- D 不提供新接口；本 Change 仅使用已登记的 `rankMoviePlan` 只读结果，不刷新推荐快照、不创建出行任务、不发邮件，也不请求位置、路线或餐饮。
