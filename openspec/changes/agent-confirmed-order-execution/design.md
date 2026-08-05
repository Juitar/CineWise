## Context

`agent-session-and-run-control` 已保存会话、运行、计划版本、步骤和 SSE 事件，但明确不创建确认动作或执行写工具。A 已书面确认 Agent 建单公开入口为 `CreateOrderTool.execute(ToolContext, CreateOrderForAgentCommand)`：Command 只含 `actionId/showId/seatIds`，当前用户由 A 的 `CurrentUserAccessor` 取得，稳定键必须从 `ToolContext` 原样复用。

本设计使用 B 自有的确认事实保护一次性动作；票务库存、金额、订单状态与最终建单结果仍由 A 独占。当前用户只经 `CurrentUserAccessor` 获取，时间只经注入 `Clock` 获取。所有跨模块调用只允许 B → A 的公开 Application API/DTO 或类型化 Tool；禁止 B 访问 A 的持久化、实体、Controller 或本机 HTTP。

## Goals / Non-Goals

**Goals:**

- 让服务端根据已经校验的创建订单 Command 建立一次性 `AgentConfirmationAction`，绑定用户、session、run、plan、planVersion、节点/工具、参数摘要和有效期。
- 用 `PENDING_CONFIRMATION`、`EXECUTING`、`RESULT_UNKNOWN`、`SUCCEEDED`、`FAILED`、`REJECTED`、`EXPIRED`、`INVALIDATED` 状态表达动作生命周期，并以 CAS 限制不可逆转换。
- 在短事务中完成归属、时效、运行/计划和参数摘要校验及 `PENDING_CONFIRMATION → EXECUTING`；A 的写调用永远在事务外。
- 对同一动作固定 `clientRequestId` 与 `idempotencyKey`，未知结果只按原键查询；失败、超时、断线、SSE 重连、重规划和重复点击均不生成新键或重发建单。
- 提供安全 REST/SSE 投影：确认卡只有 `actionId`、计划版本、到期时间、展示信息与状态；不下发 Command、参数摘要、幂等键、认证信息、完整订单和异常栈。

**Non-Goals:**

- 不实现退票、支付、退款、消息反馈、多工具编排、真实模型、实际重规划、画像/出行写入、管理轨迹和前端页面。
- 不实现未经 A 确认的真实生产建单调用，不新增临时 HTTP 调用或兼容代码。
- 不自行分配 Flyway 版本、创建或执行 `agent_action` 迁移，不连接共享数据库。

## Decisions

### 1. Command 与参数摘要只在服务端产生

`ConfirmedOrderCommand` 是 B 内部不可变对象，包含仅建单所需的 `showId`、排序后的 `seatIds` 和 A 工具名；它不含 `userId`、`ticketCount`、金额、订单状态或前端传入的哈希。`AgentActionParameterHasher` 使用 UTF-8、显式字段顺序、字符串 ID、升序座位 ID 和 SHA-256 编码产生 `hash_version=v1 + parameter_hash=<64 位小写 hex>`。创建 action 时保存摘要；确认时重新从已保存 Command 计算并比较摘要，计划版本变化或业务候选失效时拒绝。

`AgentConfirmationActionCreationService` 只接收 B 内部的 `CreateOrderConfirmationActionCommand`：先从 `CurrentUserAccessor` 读取用户，再读取本人仍在运行的 `AgentRun`、活动 `AgentSession` 和匹配的计划版本，并调用 A 的只读 `CreateOrderTool.validate`。通过后使用雪花 ID、服务端 UUID 和默认 10 分钟有效期（不晚于 run 的保留期）创建 action；创建去重键命中时返回原 action。写入 action 后才在同一提交单元写入安全 `card` 事件。当前最小运行时尚未保存 `CONFIRM_ACTION` 步骤或可恢复的建单 Command，因此由已校验计划执行器在获取到 Command 时调用该服务；不得由 HTTP 请求、模型原始输出或前端字段直接调用。

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

目标表为 B 拥有的 `agent_action`，以下字段是 SQL 静态审查前的唯一依据；暂不生成 SQL。

| 字段 | 类型、默认值和约束 | 用途 |
| --- | --- | --- |
| `id` | `BIGINT NOT NULL`，雪花主键 | 内部主键。 |
| `action_id` | `VARCHAR(36) NOT NULL`，唯一键 | 对外一次性动作标识。 |
| `user_id` | `BIGINT NOT NULL` | 只来自当前认证用户。 |
| `agent_session_id` | `BIGINT NOT NULL` | 对应 V008 `agent_session.id` 的逻辑关联，无物理外键。 |
| `agent_run_id` | `BIGINT NOT NULL` | 对应 V008 `agent_run.id` 的逻辑关联，无物理外键。 |
| `run_id` | `VARCHAR(36) NOT NULL` | 同一 `agent_run_id` 的外部运行标识，仅用于 ToolContext、SSE 和 REST 关联；不是内部关联列。 |
| `plan_id` | `VARCHAR(36) NOT NULL` | 已校验计划标识。 |
| `plan_version` | `INT NOT NULL`，`CHECK (plan_version > 0)` | 已校验计划版本。 |
| `node_id` | `VARCHAR(128) NOT NULL` | 已校验节点标识。 |
| `tool_name` | `VARCHAR(128) NOT NULL` | 受控工具标识；本次只允许服务端白名单中的创建订单 Tool。 |
| `command_snapshot` | `JSON NOT NULL`，JSON 对象 | 仅服务端已校验的 Command 快照；不含用户、金额、订单状态或前端哈希。 |
| `parameter_hash_version` | `VARCHAR(16) NOT NULL` | 当前为 `v1`。 |
| `parameter_hash` | `CHAR(64) NOT NULL`，小写 SHA-256 十六进制值 | 由快照重新计算。 |
| `expire_at` | `DATETIME(3) NOT NULL` | 仅 `PENDING_CONFIRMATION` 可在此时间前确认。 |
| `status` | `VARCHAR(32) NOT NULL` | 仅允许八个确认动作状态。 |
| `client_request_id` | `VARCHAR(64) NULL` | `EXECUTING`、`RESULT_UNKNOWN`、`SUCCEEDED`、`FAILED` 必填；其余为空。 |
| `idempotency_key` | `VARCHAR(128) NULL` | 与 `client_request_id` 同时出现，且每个 action 固定不变。 |
| `result_reference` | `VARCHAR(128) NULL` | 仅明确成功时保存 A 返回的安全结果引用；不保存完整订单。 |
| `recovery_hint` | `VARCHAR(256) NULL` | `RESULT_UNKNOWN` 必填，供安全展示；不得含异常堆栈。 |
| `result_unknown_at` | `DATETIME(3) NULL` | 进入 `RESULT_UNKNOWN` 的服务端时间；仅该状态非空。 |
| `recovery_until` | `DATETIME(3) NULL` | `result_unknown_at + 30 天`；仅该状态非空。 |
| `version` | `BIGINT NOT NULL DEFAULT 0`，`CHECK (version >= 0)` | CAS 版本。 |
| `create_time` / `update_time` | `DATETIME(3) NOT NULL` | 服务端审计时间；`update_time >= create_time`。 |

除 `version DEFAULT 0` 外，以上字段均无数据库默认值，服务端必须显式写入。唯一键为 `action_id`、`(user_id, client_request_id)`、`(user_id, idempotency_key)` 和 action 创建去重键 `(user_id, agent_run_id, plan_id, plan_version, node_id, tool_name, parameter_hash)`；MySQL 的唯一键允许 NULL，因此未进入写执行状态的动作不会占用稳定键。索引为 `(user_id, action_id)`、`(agent_run_id, plan_id, plan_version, node_id)`、`(status, expire_at)`、`(status, recovery_until)`；不建立物理外键。SQL 还必须用 CHECK 保证：`status` 只能是八个枚举值；`command_snapshot` 的 JSON 类型为对象；`parameter_hash` 为 64 位小写十六进制；`client_request_id` 与 `idempotency_key` 同时为空或同时非空；`result_reference` 只允许 `SUCCEEDED` 非空；`RESULT_UNKNOWN` 必须有 `recovery_hint`、`result_unknown_at` 和 `recovery_until=result_unknown_at+30天`，其他状态三者必须均为空。

创建 action 以已校验的 `(user_id, agent_run_id, plan_id, plan_version, node_id, tool_name, parameter_hash)` 查询或由唯一键取得原 action；存在时返回原 action，不生成新的 `action_id` 或稳定键。并发创建由该去重唯一键和创建事务保证；正式 SQL 前，`action_id` 全局唯一仍是必须项。

确认使用 `WHERE action_id=? AND status='PENDING_CONFIRMATION' AND version=? AND expire_at>?` 的条件更新并递增 `version`。受影响行数为零时重新读取 action：动作不存在/越权不泄露细节，终态返回既有安全结果，`EXECUTING`/`RESULT_UNKNOWN` 返回 `206006`。旧请求用预期版本更新，不能覆盖新状态。

确认有效期从 action 创建时的服务端 `expire_at` 开始，到 `now >= expire_at` 即不可确认；过期转换为 `EXPIRED`。A 调用出现超时、断流或响应丢失时，以结果保存短事务写入 `RESULT_UNKNOWN`、`result_unknown_at=now`、`recovery_until=now+30天` 和固定恢复提示。恢复窗口内只能按同一 action 的原 `client_request_id` 查询；查询到明确结果后转为 `SUCCEEDED` 或 `FAILED` 并清除恢复窗口字段。窗口外不再调用建单或生成新键，清理任务只能删除已过 `recovery_until` 且仍为 `RESULT_UNKNOWN` 的记录，清理前必须保留原键供恢复。

V011 已发布；原计划分配给邀请码种子的 V012 已取消，A 已将本表正式分配为 V012。A 已完成静态审查与 MySQL 8.4 迁移验证，并以 `ddd4fcf` 发布该迁移。B 不修改已发布 SQL；领域类型、Repository port、内存 Mock 和测试之外的 CAS/并发行为仍须由本 change 的 GitHub Actions MySQL job 验证。

### 4. 事务与 A 调用方向

确认流程拆为三个阶段：

1. 短事务：从 `CurrentUserAccessor` 取用户，读取 action，校验归属、运行、计划版本、节点、有效期、参数摘要和业务候选；使用 CAS 写为 `EXECUTING` 并保存稳定键。
2. 事务外：调用 `CreateOrderTool.execute(ToolContext, CreateOrderForAgentCommand)`；A 在自己的原子建单事务前调用 B 的 `AgentActionAuthorizationPort`，用当前认证用户、ToolContext、actionId、showId 和 seatIds 校验归属、`EXECUTING`、运行/节点/工具、计划/摘要和稳定键。B/A 均不跨模块访问对方持久化。
3. 新短事务：CAS 保存安全结果和 `SUCCEEDED`、`FAILED` 或 `RESULT_UNKNOWN`，再持久化 SSE 事件；只有保存成功后才对 SSE 重放可见。

数据库事务不覆盖 A 的网络等待。若进程在阶段 2 后崩溃，恢复器只以原键查询 A，不重发写调用；查不到明确结果则保留 `RESULT_UNKNOWN` 和“结果确认中”提示。

### 5. REST、SSE 与权限

`POST /api/v1/agent/actions/{actionId}/confirm` 的 body 是 `{ "confirmed": true|false }`。`false` 只在短事务中将当前 `PENDING_CONFIRMATION` 标为 `REJECTED`，不创建键、不调用 A。成功/失败响应和 SSE 都返回稳定安全文案；`206003` 是过期、`206004` 是参数变化、`206006` 是重复确认或仍在确认中。不存在和非本人动作返回同一安全资源不可用语义，运行结束、计划版本变化、业务失效使用固定 Agent 错误码/文案，具体新错误码登记需要 A/C 共同确认。

SSE 复用 `card` 与 `tool.result` 等持久化事件类型：新增的确认卡 payload 是受控白名单投影，旧卡在 action 过期或计划版本变化时显示失效。重连只回放已保存事件，不能触发确认或建单。

确认 REST 成功响应是 `AgentActionResponse(actionId, runId, planVersion, status, updatedAt)`；业务结果引用在 A Tool 返回值正式联调后再增加。确认卡 payload 固定为 `actionId`、`actionType=CREATE_ORDER`、`expireAt`、`status`、可选纯文本 `displayTitle/displayLines`；`planVersion` 只使用既有 SSE 顶层字段。确认动作内部和对 C 公开的状态都只使用同一组八个值，不存在第二套状态或映射。

## Risks / Trade-offs

- [A 的公开建单 Tool 已落地但未调用 B 授权 Port] → B 的生产适配器已只依赖 `CreateOrderTool`，但当前 `CreateOrderTool.execute` 未注入或调用 `AgentActionAuthorizationPort`。A 必须在进入 `OrderApplicationService` 前补齐该调用；B 不改 A 的订单代码，也不把本 change 的 B 侧校验当作替代。
- [本 change 的 MySQL CI 尚未运行] → V012 的迁移发布不等于 B 的确认 CAS/并发验证；未取得 workflow 运行记录前不宣称 MySQL 验证通过。
- [写结果丢失] → 固定原 action 的键并查询；查不到结论保持 `RESULT_UNKNOWN`，宁可提示处理中也不重复建单。
- [并发确认] → CAS 和唯一约束作为最终保证，单机锁和 SSE 状态不作为正确性依据；在 CI MySQL 8.4 验证并发。
- [A API 最终需要同步身份] → `ToolContext` 已预留 run/node/trace/稳定键；A 必须确认 userId 如何在公开 API 内安全获得，B 不传递前端用户字段。

## Migration Plan

1. V011 已发布，原 V012 邀请码种子已取消；A 已分配、审查并发布 V012（`ddd4fcf`）。B 不再修改该迁移。
2. B 在 GitHub Actions 的 `Backend MySQL Integration / mysql-integration` job 对空 `cinewise_agent_it` 验证首次 Flyway、重复启动、CAS、并发和恢复，并记录运行编号与结果。
3. B 部署领域与适配器；确认卡只在服务端 action 持久化后发布。A 的生产适配器经接口测试后才启用。
4. 回滚时停止创建新 action；已 `RESULT_UNKNOWN` 的 action 继续按原键查询，不删除记录、不生成替代键。

## Open Questions

1. A：在 `com.miaoyu.ticket.order.api.CreateOrderTool.execute` 中，在调用 `OrderApplicationService` 前调用 B 的 `AgentActionAuthorizationPort`；验证：A 的类型化 Tool 契约测试覆盖授权拒绝映射为 `205004`，且不创建订单。
