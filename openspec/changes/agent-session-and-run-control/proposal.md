## Why

当前 `dev` 已能保存 Agent 会话、运行、消息、步骤和 SSE 事件，也能查询单次运行；但 C 还不能创建或列出本人会话、读取完整历史、清空会话，用户也没有取消本人运行的 HTTP 入口。缺少这些控制接口时，页面无法安全地管理历史记录，也无法在运行等待时停止未开始的步骤。

## What Changes

- 新增当前用户的 Agent 会话创建、列表、历史消息查询、单个清空和批量清空接口。
- 新增当前用户取消运行接口；只把未开始步骤标为 `SKIPPED`，不回滚完成步骤、不删除已保存事实、不重试工具。
- 复用已有 Agent session/run/message/event 持久化、`CurrentUserAccessor`、资源不存在错误语义和 SSE 恢复模型；补齐应用服务、存储端口、Mapper、HTTP DTO、Controller 映射、固定 JSON 夹具和测试。
- 清空时检测活动运行：单个清空按既有 `409 / 206008` 拒绝，批量清空跳过该会话并返回清理数、跳过数；不留下悬空 `active_run_id`。
- 已清空会话不得再通过既有 POST SSE 入口创建运行、消息或事件；该入口按现有资源不存在语义拒绝。
- 不新增迁移、不修改已有迁移，也不改动确认动作、写工具、多工具规划、真实模型、重规划、前端或其他 Owner 的代码。

## Capabilities

### New Capabilities

- `agent-session-management`: 当前用户创建、列出、读取历史并清空自己的 Agent 会话。
- `agent-run-cancellation`: 当前用户取消自己的运行，并保持步骤、运行、事件和恢复事实一致。

### Modified Capabilities

无。

## Impact

- 影响 B 的 `backend/src/main/java/com/miaoyu/ticket/agent` 中 `api`、`application`、`domain`、`infrastructure/persistence`，以及对应 Agent 测试和 C 可消费夹具。
- 对 C 新增 REST JSON 契约和固定夹具；复用其 `CurrentUserAccessor`、Cookie/CSRF 与统一未认证响应，不改 C 的前端或认证实现。
- 不访问 A/D 的 Entity、Mapper、Repository、Controller 或业务 API；不产生数据库迁移，也不改变既有 SSE 事件续传规则。
- 验收为：本人正常使用、越权按资源不存在处理、清空后提交拒绝且无新增事实、活动运行清空、重复取消、终态取消、同会话并发取消/清空和 SSE 恢复后的事实一致性均有自动测试；`openspec validate --strict` 与后端 `verify` 通过。
