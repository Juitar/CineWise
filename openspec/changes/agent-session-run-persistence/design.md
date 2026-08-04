## Context

现有 `MinimalReadOnlyAgentService` 已能在单次内存调用中生成候选计划、重新校验、执行 `rankMoviePlan` 并返回结构化回复，但没有会话、运行、消息或步骤存储。请求结束后无法可靠回答“这次是否已执行”“当前节点是什么”“重试是否应返回原运行”，也不能为 SSE、确认动作或恢复提供事实基础。

本 Change 由 B 实现，复用 C 已提供的 `CurrentUserAccessor`、B 已有 `PlanSchemaValidator`、`ExecutionPlanStateMachine` 与 `MinimalReadOnlyAgentService`。D 的 `RankMoviePlanTool` 仍是唯一跨模块只读调用；D 已确认 B 不创建出行任务、不刷新快照、不发邮件，也不请求位置。

## Goals / Non-Goals

**Goals:**

- 保存当前用户的 Agent 会话、最小只读运行、可展示消息和已校验步骤快照。
- 用数据库约束和条件更新处理重复 `clientRequestId` 与单会话活动运行，不依赖 Redis 作为最终依据。
- 在不扩大工具调用范围的前提下，让当前最小只读流程可以从持久化会话启动并写回运行结果。
- 为后续 SSE 事件、确认动作、写工具恢复和轨迹查询留下稳定的 B 自有数据边界。

**Non-Goals:**

- 不实现 Controller、SSE、事件表、断线续传、Redis 活跃上下文或后台恢复器。
- 不实现 `actionId`、确认动作、订单/退款等写工具，或调用 A 的 Controller、Mapper、Repository。
- 不接 D 的出行任务、快照、邮件、位置、路线或餐饮能力。
- 不保存模型原始思维、完整工具响应、认证秘密、精确位置或完整第三方响应。
- 不创建 `agent_event`、`agent_action`、`agent_feedback` 或 `agent_tool_call`；它们由后续 Change 通过新的前向迁移补齐。

## Decisions

### 1. 用四张 B 自有表保存最小运行事实

本 Change 只引入 `agent_session`、`agent_run`、`agent_message` 和 `agent_run_step`。

- `agent_session`：内部 `id BIGINT`、对外唯一 `session_id VARCHAR(36)`、`user_id BIGINT`、最小摘要、`status`、`active_run_id BIGINT NULL`、`create_time`、`update_time`、`expire_at`。`active_run_id` 逻辑关联 `agent_run.id`，允许空值；使用 `CHECK (active_run_id IS NULL OR active_run_id > 0)`，并建唯一索引，防止同一运行被多个会话占用。
- `agent_run`：内部 `id BIGINT`、对外唯一 `run_id VARCHAR(36)`、`session_id BIGINT`、`user_id BIGINT`、`client_request_id VARCHAR(36)`、请求摘要哈希、计划 ID/版本、`status`、开始/结束时间、`trace_id`、`create_time`、`update_time`、`expire_at`。对 `(user_id, session_id, client_request_id)` 建唯一键，确保同一请求返回原 runId。
- `agent_message`：内部 `id BIGINT`、对外唯一 `message_id VARCHAR(36)`、`session_id BIGINT`、`run_id BIGINT`、`user_id BIGINT`、角色、内部消息类型、展示文本、受控 payload JSON、消息状态、`create_time`、`expire_at`。
- `agent_run_step`：内部 `id BIGINT`、`run_id BIGINT`、计划版本、节点 ID/类型、依赖和输入引用 JSON、节点状态、失败策略、尝试/重试次数、跳过字段和脱敏槽位快照 JSON、`create_time`、`update_time`、`expire_at`；对 `(run_id, plan_version, node_id)` 建唯一键。

四表的内部 ID、关联 ID 和版本/计数均使用正数 CHECK；状态、角色、消息类型、节点类型和失败策略使用与现有枚举一致的 CHECK。所有表建立 `expire_at` 索引；查询索引包括会话的 `(user_id, update_time)`、运行的 `(session_id, status, create_time)` 与消息的 `(session_id, id)`、步骤的 `(run_id, status)`。

表不建立物理外键，跨表完整性由 Application Service、逻辑关联和必要唯一索引保证。清理任务按 `agent_run_step → agent_message → agent_run → agent_session` 删除已到期数据。`agent_event`、`agent_action`、`agent_feedback`、`agent_tool_call` 留给 SSE、确认、反馈和轨迹 Change，避免提前引入没有消费者的字段。

备选方案是一次性创建详设全部 Agent 表。未采用，因为事件、写工具确认和工具审计的状态与保留规则尚未实现，提前建表会把未验证的字段和索引锁进迁移。

### 2. 当前用户只能来自 C 的认证上下文

Application Service 依赖 `CurrentUserAccessor`，使用其返回的用户 ID 查询或创建 B 的记录。Repository 方法均带 `userId + sessionId/runId` 条件；查询不到按“资源不存在”处理，避免暴露其他用户的会话是否存在。

不采用由 Controller 传递 `userId`，因为本 Change 暂不新增 Controller，且请求体中的身份不可信。也不让模型或工具上下文携带用户身份。

### 3. 用初始短事务占用运行，再在事务外执行只读工具

提交流程分三段：

1. 短事务：确认会话归属，按 `user_id + session_id + client_request_id` 查询重复请求并校验请求摘要；对新请求先插入 `RUNNING` 的 `agent_run`，再执行 `UPDATE agent_session SET active_run_id = :runInternalId WHERE id = :sessionInternalId AND user_id = :userId AND active_run_id IS NULL`。插入、条件更新和用户消息必须在同一事务；条件更新影响行数为零时整体回滚并返回 `409 / 206008`。
2. 事务外：调用既有最小只读主控；它按现有白名单、计划校验和类型化适配器规则调用 D 的只读工具。
3. 新短事务：保存候选计划/步骤、结构化回复和最终运行状态；终态运行只能使用 `UPDATE agent_session SET active_run_id = NULL WHERE id = :sessionInternalId AND active_run_id = :runInternalId` 清空占用，旧运行影响行数为零时不得清除新运行。`PROCESSING` 保持占用。

这样不把模型或 D 的网络调用放入数据库事务，也不会因下游失败回滚“用户已经提交了本次请求”的事实。备选方案是一个大事务包裹完整主控，未采用，因为会长时间持有数据库连接并放大锁冲突。

### 4. 数据库负责重复请求和活动运行的最终判断

`agent_run` 对 `user_id + session_id + client_request_id` 建唯一索引，保存请求摘要哈希。相同摘要重复请求返回既有运行；摘要不同拒绝，防止客户端错误复用请求标识。并发插入触发唯一键冲突时重新读取既有运行并按摘要做同样判断。会话占用通过条件更新，并在 Repository 返回受影响行数为零时读取现有活动运行并返回 `206008`。

不采用仅 Redis 锁或内存 Map：进程重启、Redis 故障和多实例都会使它们无法作为最终依据。Redis 活跃上下文会在后续 Change 作为加速层接入。

### 5. 迁移先停在规划和接口层，等待 A 正式分配版本

仓库规则要求 A 分配 Flyway 版本并授权 MySQL 验证。V008 当前只是候选，不能据此创建迁移文件。本 Change 的设计和任务记录完整的表、索引和保留要求；A 未正式确认时不得创建迁移文件或执行共享数据库。Change 推送至可审查分支、总体设计同步并通过复核后，A 才能正式分配 V008；随后再创建仅属于 B 的受控迁移并做空 MySQL 和重复初始化验证。

## Risks / Trade-offs

- [A 未正式分配 V008] → 只能完成规划和不依赖表的纯 Java 类型；表、Mapper、Repository 和集成测试暂停，直到 Change 可审查、总体设计同步并经 A 复核。
- [运行在工具调用后进程崩溃] → 初始 `RUNNING` 事实仍保留，不会错误重发；恢复器和写工具查询不在本 Change，后续 Change 以该记录为输入。
- [只读工具返回 PROCESSING] → 运行保持活动且阻止新消息；本 Change 不实现恢复入口，调用方只能查询快照，后续 SSE/恢复 Change 解决继续执行。
- [MySQL 不支持部分唯一索引] → 以会话活动 run 条件更新保证单会话活动约束，而非依赖 `RUNNING` 状态的部分唯一索引。
- [JSON 快照包含敏感原文] → 只保存白名单的槽位名、业务 ID、节点和安全回复字段；模型原始消息和工具原始响应不进入 JSON。

## Migration Plan

1. B 完成数据字段、索引、30 天保留和测试方案的 OpenSpec 规划。
2. B 推送完整 Change 并同步总体设计；A 复核无物理外键、ID、时间字段、状态 CHECK、索引、30 天清理顺序和执行窗口后，正式分配 V008。
3. B 创建迁移脚本及 Entity/Mapper/Repository；A 授权后在空 MySQL 验证，再做重复初始化和旧应用兼容检查。
4. 发布时先部署兼容表结构，再部署 B 应用代码；本 Change 不读取或修改其他模块表。
5. 回退应用时保留新增表，不回滚已执行迁移；后续结构修复只通过新的向前迁移完成。

## Open Questions

- A：在 Change 和总体设计已进入可审查分支并复核后，请正式确认 V008；版本分配不等于 SQL 执行或共享库发布授权。
- C：`CurrentUserAccessor` 的“未认证”异常与现有 `Result<T>` 映射是否可直接复用；本 Change 不新增 HTTP 接口，不阻塞数据层设计。
