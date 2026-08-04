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

- 不实现 Controller、SSE、事件表、断线续传、Redis 活跃上下文或持续运行的恢复任务；本 Change 只包含启动和新消息提交时的陈旧运行恢复。
- 不实现 `actionId`、确认动作、订单/退款等写工具，或调用 A 的 Controller、Mapper、Repository。
- 不接 D 的出行任务、快照、邮件、位置、路线或餐饮能力。
- 不保存模型原始思维、完整工具响应、认证秘密、精确位置或完整第三方响应。
- 不创建 `agent_event`、`agent_action`、`agent_feedback` 或 `agent_tool_call`；它们由后续 Change 通过新的前向迁移补齐。

## Decisions

### 1. 用四张 B 自有表保存最小运行事实

本 Change 只引入 `agent_session`、`agent_run`、`agent_message` 和 `agent_run_step`。

V008 已由 A 正式分配；静态审查和版本分配不等于共享库发布授权。相邻《Agent 智能决策中心系统分析设计》第 5.2 节“最小四表候选迁移字段表”是本 Change 唯一字段来源，后续完整模型章节不能用于本次迁移。

- `agent_session`：内部 `id BIGINT`、对外唯一 `session_id VARCHAR(36)`、`user_id BIGINT`、最小摘要、`status`、`active_run_id BIGINT NULL`、`create_time`、`update_time`、`expire_at`。`active_run_id` 逻辑关联 `agent_run.id`，允许空值；使用 `CHECK (active_run_id IS NULL OR active_run_id > 0)`，并建唯一索引，防止同一运行被多个会话占用。
- `agent_run`：内部 `id BIGINT`、对外唯一 `run_id VARCHAR(36)`、`session_id BIGINT`、`user_id BIGINT`、`client_request_id VARCHAR(36)`、请求摘要哈希、计划 ID/版本、`status`、开始/结束时间、`trace_id`、`create_time`、`update_time`、`expire_at`。对 `(user_id, session_id, client_request_id)` 建唯一键，确保同一请求返回原 runId。
- `agent_message`：内部 `id BIGINT`、对外唯一 `message_id VARCHAR(36)`、`session_id BIGINT`、`run_id BIGINT`、`user_id BIGINT`、角色、内部消息类型、展示文本、受控 payload JSON、消息状态、`create_time`、`expire_at`。
- `agent_run_step`：内部 `id BIGINT`、`run_id BIGINT`、计划版本、节点 ID/类型、依赖和输入引用 JSON、节点状态、失败策略、尝试/重试次数、跳过字段和脱敏槽位快照 JSON、`create_time`、`update_time`、`expire_at`；对 `(run_id, plan_version, node_id)` 建唯一键。

四表的内部 ID、关联 ID 和版本/计数均使用正数 CHECK；状态、角色、消息类型、节点类型和失败策略使用与现有枚举一致的 CHECK。所有表建立 `expire_at` 索引；查询索引包括会话的 `(user_id, update_time)`、运行的 `(session_id, status, create_time)` 与消息的 `(session_id, id)`、步骤的 `(run_id, status)`。

表不建立物理外键，跨表完整性由 Application Service、逻辑关联和必要唯一索引保证。清理任务按 `agent_run_step → agent_message → agent_run → agent_session` 删除已到期数据。`agent_event`、`agent_action`、`agent_feedback`、`agent_tool_call` 留给 SSE、确认、反馈和轨迹 Change，避免提前引入没有消费者的字段。

备选方案是一次性创建详设全部 Agent 表。未采用，因为事件、写工具确认和工具审计的状态与保留规则尚未实现，提前建表会把未验证的字段和索引锁进迁移。

### 1.1 状态、终态时间和步骤并发更新

- 会话状态只允许 `ACTIVE`、`CLEARED`。`CLEARED` 必须没有 `active_run_id`；`ACTIVE` 可有或没有活动运行。
- 运行状态只允许 `RUNNING`、`COMPLETED`、`FAILED`、`CANCELLED`。`RUNNING` 时 `finished_at` 必须为空；其他终态时 `finished_at` 必须非空。
- V008 的消息为一次写入的最终可展示消息：角色只允许 `USER`、`ASSISTANT`，状态固定 `COMPLETED`；`USER` 只能使用 `TEXT`，`ASSISTANT` 只能使用 `TEXT`、`QUESTION`、`MOVIE_CARD`、`PLAN_CARD`、`PROGRESS`、`ERROR`。`STREAMING`、`SYSTEM`、路线/订单卡片和事件关联字段留给后续迁移。
- 步骤状态只允许 `PENDING`、`RUNNING`、`SUCCESS`、`FAILED`、`SKIPPED`。`PENDING` 没有开始/结束时间；`RUNNING` 有开始时间而无结束时间；`SUCCESS`、`FAILED` 有开始和结束时间；`SKIPPED` 没有开始时间但有结束时间。`auto_skipped=true` 时状态必须为 `SKIPPED`，并且 `skip_reason=UPSTREAM_FAILED`、`skip_source_node_id` 非空；其他状态必须没有跳过字段。
- `agent_run_step` 使用 `version` CAS 加状态前置条件更新，不只依赖状态字符串。所有推进以 `WHERE id=:id AND version=:expectedVersion AND status=:expectedStatus` 更新并使 `version=version+1`；影响行数为零时重新读取，不覆盖其他调度器已保存的结果。

### 1.2 RUNNING、PROCESSING、崩溃和清理

`ToolResult.PROCESSING` 保存为 `agent_run.status=RUNNING`、对应步骤 `status=RUNNING`、`recovery_pending=true`。`AgentRunStaleRecoveryService` 只在应用启动和新消息提交前运行，不是定时任务；仅处理 `status=RUNNING AND update_time <= now - 30 秒` 的陈旧运行。它在短事务内以 run/step 的 `version + status` CAS 将未完成只读步骤和运行更新为 `FAILED`，写入安全错误消息，并使用当前内部 run ID 条件清空会话占用；它不重放任何工具。未达到陈旧阈值的 `RUNNING` 记录保持不变。每个运行创建时确定唯一 `run_expire_at=started_at+30天`，并将该值原样写入其消息和步骤；会话 `expire_at` 保持为其全部运行到期时间的最大值。只有终态运行才可在到期后清理；清理会话前必须同时满足 `active_run_id IS NULL`、`session.expire_at < now` 且不存在未清理运行，绝不留下悬空引用或子记录。

### 1.3 request_hash V1

`request_hash_version` 固定为 `v1`。服务端用 UTF-8 对以下规范化 JSON 计算小写十六进制 SHA-256：`{"v":"v1","content":"...","slotSnapshotVersion":n,"slots":{...}}`。字段顺序固定为 `v`、`content`、`slotSnapshotVersion`、`slots`；`slots` 的键按 Unicode 码点升序，字符串先做 Unicode NFC 和 `CRLF/CR` 到 `LF` 统一，但不 trim、不折叠空白。内容、槽位版本和槽位值均参与哈希；`runId`、`traceId`、超时预算和服务端白名单不参与。重复请求必须同时比较版本和哈希，任一不一致即拒绝。

### 2. 当前用户只能来自 C 的认证上下文

Application Service 依赖 `CurrentUserAccessor`，使用其返回的用户 ID 查询或创建 B 的记录。Repository 方法均带 `userId + sessionId/runId` 条件；查询不到按“资源不存在”处理，避免暴露其他用户的会话是否存在。C 已确认未认证时 `requireCurrentUserId()` 统一返回 `401 / 201006 / SESSION_INVALID`；B 直接复用，不新建身份解析逻辑，也不映射为 Agent 私有错误码。

不采用由 Controller 传递 `userId`，因为本 Change 暂不新增 Controller，且请求体中的身份不可信。也不让模型或工具上下文携带用户身份。

### 3. 用初始短事务占用运行，再在事务外执行只读工具

提交流程分三段：

1. 短事务：确认会话归属，按 `user_id + session_id + client_request_id` 查询重复请求并校验请求摘要；对新请求先插入 `RUNNING` 的 `agent_run`，再执行 `UPDATE agent_session SET active_run_id = :runInternalId WHERE id = :sessionInternalId AND user_id = :userId AND active_run_id IS NULL`。插入、条件更新和用户消息必须在同一事务；条件更新影响行数为零时整体回滚并返回 `409 / 206008`。
2. 事务外：调用既有最小只读主控；它按现有白名单、计划校验和类型化适配器规则调用 D 的只读工具。
3. 新短事务：保存候选计划/步骤、结构化回复和最终运行状态；终态运行只能使用 `UPDATE agent_session SET active_run_id = NULL WHERE id = :sessionInternalId AND active_run_id = :runInternalId` 清空占用，旧运行影响行数为零时不得清除新运行。`PROCESSING` 保持占用。

这样不把模型或 D 的网络调用放入数据库事务，也不会因下游失败回滚“用户已经提交了本次请求”的事实。备选方案是一个大事务包裹完整主控，未采用，因为会长时间持有数据库连接并放大锁冲突。

### 4. 数据库负责重复请求和活动运行的最终判断

`agent_run` 对 `user_id + session_id + client_request_id` 建唯一索引，保存请求摘要哈希。相同摘要重复请求返回既有运行；摘要不同返回 `409 / 206009`，防止客户端错误复用请求标识。并发插入触发唯一键冲突时重新读取既有运行并按摘要做同样判断。会话占用通过条件更新，并在 Repository 返回受影响行数为零时读取现有活动运行并返回 `206008`。

不采用仅 Redis 锁或内存 Map：进程重启、Redis 故障和多实例都会使它们无法作为最终依据。Redis 活跃上下文会在后续 Change 作为加速层接入。

### 5. Agent MySQL 集成测试使用 CI 一次性容器

A 已正式分配 V008，并完成静态审查；已审查 SQL 的 SHA-256 为 `41E38D53F00C68A82E63F51847E7A27525B68336B176A68592A4990D871E1797`，本 Change 不修改该文件。A 已完成 V001–V008 的空 MySQL 8.4 验证；B 的 Agent 持久化集成测试不再请求或连接云端验证库。

现有 `backend-mysql-integration.yml` 保留票务测试使用的 `cinewise_ticketing_concurrency_check`。工作流在同一 MySQL 8.4 服务中额外创建 `cinewise_agent_it`，仅给现有临时账号 `cinewise_ci` 授权。Agent 测试以单独 Maven 步骤运行，设置 `MYSQL_DATABASE=cinewise_agent_it` 和 `CINEWISE_MYSQL_AGENT_PERSISTENCE_IT=true`；测试安全初始化器同时校验 JDBC 是 MySQL、库名和账号精确匹配，避免误连共享库或票务测试库。

工作流随后以同一临时库第二次启动相同测试类。两次 Spring 上下文均开启 Flyway：第一次执行首次迁移，第二次只校验既有历史，从而验证重复初始化不重复执行迁移。容器、数据库和账号均随 CI Job 销毁；不读取、请求、输出或保存云端 `cinewise_migration_check` 凭据。

## Risks / Trade-offs

- [CI 临时库初始化失败] → Agent Maven 步骤不会运行，CI 应直接失败；检查服务健康、建库授权和 `AgentPersistenceMySqlIntegrationTest` 的安全守卫，不连接云端库绕过。
- [运行在工具调用后进程崩溃] → 初始 `RUNNING` 事实仍保留；仅启动或新消息提交时发现 `update_time` 已超过 30 秒的记录，才条件更新为失败并释放活动运行位，不自动重发工具。
- [只读工具返回 PROCESSING] → 运行保持活动；陈旧恢复只结束超过阈值的记录，不调用工具。
- [MySQL 不支持部分唯一索引] → 以会话活动 run 条件更新保证单会话活动约束，而非依赖 `RUNNING` 状态的部分唯一索引。
- [JSON 快照包含敏感原文] → 只保存白名单的槽位名、业务 ID、节点和安全回复字段；模型原始消息和工具原始响应不进入 JSON。

## Migration Plan

1. B 完成数据字段、索引、30 天保留和测试方案的 OpenSpec 规划。
2. B 推送完整 Change 并同步总体和 Agent 详细设计；A 复核无物理外键、逐字段定义、状态 CHECK、索引、30 天清理顺序、CAS、陈旧恢复规则和请求哈希后，正式分配本 Change 的 V008。
3. B 创建迁移脚本及 Entity/Mapper/Repository；A 完成 V001–V008 空 MySQL 验证后，B 在 CI 一次性 MySQL 8.4 容器的 `cinewise_agent_it` 运行 Agent 集成测试和重复初始化验证。
4. 发布时先部署兼容表结构，再部署 B 应用代码；本 Change 不读取或修改其他模块表。
5. 回退应用时保留新增表，不回滚已执行迁移；后续结构修复只通过新的向前迁移完成。

## Open Questions

- 无。C 已确认 `CurrentUserAccessor` 的未认证请求复用 `401 / 201006 / SESSION_INVALID`；本 Change 不新增 HTTP 接口。
