## Context

`agent-session-and-run-control` 已保存会话、运行、计划版本、步骤和 SSE 事件，但明确不创建确认动作或执行写工具。A 已书面确认 Agent 建单公开入口为 `CreateOrderTool.execute(ToolContext, CreateOrderForAgentCommand)`：Command 只含 `actionId/showId/seatIds`，当前用户由 A 的 `CurrentUserAccessor` 取得，稳定键必须从 `ToolContext` 原样复用。

本设计使用 B 自有的确认事实保护一次性动作；票务库存、金额、订单状态与最终建单结果仍由 A 独占。当前用户只经 `CurrentUserAccessor` 获取，时间只经注入 `Clock` 获取。所有跨模块调用只允许 B → A 的公开 Application API/DTO 或类型化 Tool；禁止 B 访问 A 的持久化、实体、Controller 或本机 HTTP。

## Goals / Non-Goals

**Goals:**

- 让服务端根据已经校验的创建订单 Command 建立一次性 `AgentConfirmationAction`，绑定用户、session、run、plan、planVersion、节点/工具、参数摘要和有效期。
- 用 `PENDING`、`REJECTED`、`CLAIMED`、`RESULT_UNKNOWN`、`SUCCEEDED`、`FAILED`、`EXPIRED` 状态表达动作生命周期，并以 CAS 限制不可逆转换。
- 在短事务中完成归属、时效、运行/计划和参数摘要校验及 `PENDING → CLAIMED`；A 的写调用永远在事务外。
- 对同一动作固定 `clientRequestId` 与 `idempotencyKey`，未知结果只按原键查询；失败、超时、断线、SSE 重连、重规划和重复点击均不生成新键或重发建单。
- 提供安全 REST/SSE 投影：确认卡只有 `actionId`、计划版本、到期时间、展示信息与状态；不下发 Command、参数摘要、幂等键、认证信息、完整订单和异常栈。

**Non-Goals:**

- 不实现退票、支付、退款、消息反馈、多工具编排、真实模型、实际重规划、画像/出行写入、管理轨迹和前端页面。
- 不实现未经 A 确认的真实生产建单调用，不新增临时 HTTP 调用或兼容代码。
- 不自行分配 Flyway 版本、创建或执行 `agent_action` 迁移，不连接共享数据库。

## Decisions

### 1. Command 与参数摘要只在服务端产生

`ConfirmedOrderCommand` 是 B 内部不可变对象，包含仅建单所需的 `showId`、排序后的 `seatIds` 和 A 工具名；它不含 `userId`、`ticketCount`、金额、订单状态或前端传入的哈希。`AgentActionParameterHasher` 使用 UTF-8、显式字段顺序、字符串 ID、升序座位 ID 和 SHA-256 编码产生 `hash_version=v1 + parameter_hash=<64 位小写 hex>`。创建 action 时保存摘要；确认时重新从已保存 Command 计算并比较摘要，计划版本变化或业务候选失效时拒绝。

不以 JSON 序列化字节直接做摘要，避免字段顺序、空值和库升级造成同一参数产生不同结果。

### 2. 动作状态机与恢复

```text
PENDING_CONFIRMATION --拒绝--> REJECTED
PENDING_CONFIRMATION --到期--> EXPIRED
PENDING_CONFIRMATION --参数/计划/业务变化--> INVALIDATED
PENDING_CONFIRMATION --CAS确认通过--> EXECUTING
EXECUTING --A明确成功--> SUCCEEDED
EXECUTING --A明确业务失败--> FAILED
EXECUTING --超时/断线/响应未知--> RESULT_UNKNOWN
RESULT_UNKNOWN --原键查询明确成功--> SUCCEEDED
RESULT_UNKNOWN --原键查询明确失败--> FAILED
```

`REJECTED`、`EXPIRED`、`INVALIDATED`、`SUCCEEDED`、`FAILED` 是终态。`RESULT_UNKNOWN` 不能被普通失败处理覆盖；只能由 A 的原幂等键查询得到明确结论后推进。重复确认读取已提交的胜者结果或由 B 的确认接口返回 `206006`，绝不再调用写工具。A 的 Tool 授权失败统一返回 `205004`，不泄露 action 是否存在。

### 3. 持久化与并发

目标表为 B 拥有的 `agent_action`：内部 BIGINT 雪花 ID、`action_id VARCHAR(36)`、`user_id`、与 V008 一致的 BIGINT `session_id/run_id` 逻辑关联、`plan_id`、`plan_version`、`node_id`、`tool_name`、JSON Command 快照、`parameter_hash_version`、`parameter_hash CHAR(64)`、`expire_at`、`status`、`client_request_id`、`idempotency_key`、结果引用、恢复提示、`version`、`create_time`、`update_time`。唯一键为 `action_id`、`(user_id, client_request_id)`、`(user_id, idempotency_key)`；索引为 `(user_id, action_id)`、`(run_id, plan_id, plan_version, node_id)`、`(status, expire_at)`；不建立物理外键。

确认 Claim 使用 `WHERE action_id=? AND status='PENDING' AND version=? AND expire_at>?` 的条件更新并递增 `version`。受影响行数为零时重新读取 action：动作不存在/越权不泄露细节，终态返回既有安全结果，`CLAIMED`/`RESULT_UNKNOWN` 返回 `206006`。旧请求用预期版本更新，不能覆盖新状态。

`agent_action` 的候选版本是 V013，但仍须 A 在远端看到完整 OpenSpec 并完成静态审查后正式分配。此前只实现领域类型、Repository port、内存 Mock 和测试；不把内存实现称为持久化实现。

### 4. 事务与 A 调用方向

确认流程拆为三个阶段：

1. 短事务：从 `CurrentUserAccessor` 取用户，读取 action，校验归属、运行、计划版本、节点、有效期、参数摘要和业务候选；使用 CAS 写为 `CLAIMED` 并保存稳定键。
2. 事务外：调用 `CreateOrderTool.execute(ToolContext, CreateOrderForAgentCommand)`；A 在自己的原子建单事务前调用 B 的 `AgentActionAuthorizationPort`，用当前认证用户、ToolContext、actionId、showId 和 seatIds 校验归属、`EXECUTING`、运行/节点/工具、计划/摘要和稳定键。B/A 均不跨模块访问对方持久化。
3. 新短事务：CAS 保存安全结果和 `SUCCEEDED`、`FAILED` 或 `RESULT_UNKNOWN`，再持久化 SSE 事件；只有保存成功后才对 SSE 重放可见。

数据库事务不覆盖 A 的网络等待。若进程在阶段 2 后崩溃，恢复器只以原键查询 A，不重发写调用；查不到明确结果则保留 `RESULT_UNKNOWN` 和“结果确认中”提示。

### 5. REST、SSE 与权限

`POST /api/v1/agent/actions/{actionId}/confirm` 的 body 是 `{ "confirmed": true|false }`。`false` 只在短事务中将当前 `PENDING` 标为 `REJECTED`，不创建键、不调用 A。成功/失败响应和 SSE 都返回稳定安全文案；`206003` 是过期、`206004` 是参数变化、`206006` 是重复确认或仍在确认中。不存在和非本人动作返回同一安全资源不可用语义，运行结束、计划版本变化、业务失效使用固定 Agent 错误码/文案，具体新错误码登记需要 A/C 共同确认。

SSE 复用 `card` 与 `tool.result` 等持久化事件类型：新增的确认卡 payload 是受控白名单投影，旧卡在 action 过期或计划版本变化时显示失效。重连只回放已保存事件，不能触发确认或建单。

## Risks / Trade-offs

- [A 尚未确认 Agent 建单公开接口] → 只完成 B 的端口、Mock、参数摘要、状态机和测试；任务保持未完成，不写生产调用。
- [迁移版本、字段或索引未确认] → 不写 SQL、不连接共享数据库；记录待 A 分配，MySQL CI 不宣称通过。
- [写结果丢失] → 固定原 action 的键并查询；查不到结论保持 `RESULT_UNKNOWN`，宁可提示处理中也不重复建单。
- [并发确认] → CAS 和唯一约束作为最终保证，单机锁和 SSE 状态不作为正确性依据；在 CI MySQL 8.4 验证并发。
- [A API 最终需要同步身份] → `ToolContext` 已预留 run/node/trace/稳定键；A 必须确认 userId 如何在公开 API 内安全获得，B 不传递前端用户字段。

## Migration Plan

1. A 确认建单 Tool/API、DTO、错误码、原键查询、结果未知和 action 验证职责，并分配迁移版本。
2. A 静态审查 `agent_action` SQL；B 和 A 在 GitHub Actions 的 `Backend MySQL Integration` / `Agent MySQL Integration`（以当前 workflow 实际 job 名为准）对空 `cinewise_agent_it` 验证首次 Flyway、重复启动、CAS、并发和恢复。
3. B 部署领域与适配器；确认卡只在服务端 action 持久化后发布。A 的生产适配器经接口测试后才启用。
4. 回滚时停止创建新 action；已 `RESULT_UNKNOWN` 的 action 继续按原键查询，不删除记录、不生成替代键。

## Open Questions

1. A：在远端 OpenSpec 可见并通过严格校验后，正式分配 V013，审查 SQL；`RESULT_UNKNOWN` 必须保留 30 天且仅允许原键查询恢复。
2. C：请确认确认接口和 SSE 的稳定展示错误码映射、Cookie/CSRF 行为以及前端对“结果确认中/已失效”的展示文案；本 change 不实现前端。
