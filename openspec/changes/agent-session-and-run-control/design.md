## Context

`dev` 已有 `agent_session`、`agent_run`、`agent_message`、`agent_run_step`、`agent_event` 与事件游标；`AgentSessionCreationService` 已可从 `CurrentUserAccessor` 创建会话，`AgentSessionQueryService` 和 `AgentRuntimeQueryService` 已能按当前用户查询单个会话/运行。已有 `AgentInteractionRuntimeService` 和 Controller 只暴露 POST SSE 与单次运行快照，未提供会话控制接口或取消规则。

本次由 B 实现。C 消费新增 REST JSON 和固定夹具，但本次不改 C 前端、认证或公共 HTTP 客户端。A 不参与迁移：当前表已有状态、CAS、到期时间和事件表，禁止新增或修改迁移。D 不提供新接口。

## Goals / Non-Goals

**Goals:**

- 为当前用户提供创建、列表、历史消息、单个/批量清空会话和取消运行的 HTTP API。
- 复用 `CurrentUserAccessor`、`206005`、`206008`、`PageResult`、已有持久化事实和 SSE 事件恢复。
- 使用事务、行条件和版本 CAS 保证清空/取消的并发结果可恢复、可重复读取。

**Non-Goals:**

- 不实现 `agent_action`、确认、反馈、写工具、票务/支付调用、多工具规划、真实模型、重规划、管理员查询或前端代码。
- 不新增迁移、不修改 V008/V009 或共享迁移，不物理删除会话即时删除历史数据。
- 不强制中断已运行的只读工具，也不在网络异常后自动重发取消。

## Decisions

### 1. HTTP 只调用新的 Agent 应用门面

`AgentController` 继续只依赖 `AgentInteractionRuntimeService`。门面新增 `createMySession`、`listMySessions`、`listMySessionMessages`、`clearMySession`、`clearMySessions`、`cancelMyRun`，将持久化领域对象显式映射为 `AgentSessionResponse`、`AgentMessageResponse`、`AgentSessionClearResponse`、`AgentSessionBulkClearResponse`、`AgentRunCancelResponse`。会话 DTO 不暴露内部 `active_run_id`。创建接口没有请求体，避免把未定义的标题或用户字段写入会话摘要。

列表和消息均使用 `page`、`size`，默认 1/20、最大 100，返回现有 `PageResult`。会话只返回 `ACTIVE`；历史查询先以 `sessionId + userId + ACTIVE` 定位，其他用户、已清空和不存在统一 `206005`。这比先查询再判断权限安全，也不需要 C 提供额外字段。

### 2. 清空是逻辑清空并保留可审计事实

`AgentSessionManagementService` 在事务中从 `CurrentUserAccessor` 取得用户，按会话、用户、`ACTIVE` 和 `active_run_id IS NULL` 条件更新会话为 `CLEARED`。同一事务把该会话下运行、消息、步骤、事件和事件游标的 `expire_at` 设为当前时间，供已有清理任务处理；不物理删除，也不写 SSE 事件。单个操作条件不满足时重新读取本人活动会话：仍有活动运行返回 `206008`，其他情况按 `206005`；批量操作逐个条件更新，可成功的记为清空、竞争中变为活动的记为跳过。

既有 POST SSE 提交入口也只接受 `ACTIVE` 会话：初始事务读到 `CLEARED` 时按 `206005` 拒绝；最终占用 `active_run_id` 的条件更新再次要求 `status = ACTIVE`，避免读取后被并发清空的会话重新写入运行、消息或事件。所有 Repository 新增方法均在 B 的 application 端口，MyBatis 实现在 infrastructure，Controller 不触碰 Mapper。

### 3. 取消只推进 PENDING 步骤并以 CAS 写入运行终态

`AgentRunCancellationService` 在一个事务内按 `runId + userId` 读取运行。若运行已终态，直接返回当前事实。若为 `RUNNING`，读取步骤；每个 `PENDING` 步骤基于原 `version + PENDING` CAS 更新为 `SKIPPED`，保持 `RUNNING`/`SUCCESS`/`FAILED` 不变；随后基于运行原 `version + RUNNING` CAS 写为 `CANCELLED` 并仅在 `active_run_id` 仍指向该内部运行时释放。

CAS 失败时不重发取消命令：服务只重新读取当前运行并返回已保存状态。这样重复/并发取消不会产生第二次状态推进。`AgentRuntimeEventService` 在运行和步骤事实已准备好后追加一个现有 `run.complete` 事件，载荷仅为 `{"status":"CANCELLED"}`；该事件与事实在同一事务提交，所以 SSE 只能读到已提交的取消结果。

### 4. 事务和恢复边界

创建、清空和取消事务定义在 Application Service。查询标注 `readOnly=true`，不得调用模型、工具或事件服务。取消不处理正在执行的只读步骤；它保留 `RUNNING` 步骤和既有恢复标记，避免凭空中断工具。清空不会清理活动运行：单个请求拒绝、批量请求跳过。取消提交后释放活动引用，使后续清空可执行；并发清空要么先看到 `206008`，要么在取消完成后按条件更新成功。

### 5. 无迁移与测试

既有表已有 `status`、`active_run_id`、版本、到期时间和事件表，因此本次只增加 Mapper SQL、Repository 端口和应用服务，不生成任何 Flyway 文件。测试覆盖纯服务的权限、分页、清空、取消、重复取消、终态保护和事件；MockMvc 覆盖 DTO、路径、统一错误；MySQL 集成测试覆盖同会话取消/清空竞争和条件更新；夹具给 C 验证 JSON 字段和终态展示。

## Risks / Trade-offs

- [取消与运行完成同时发生] → 使用 `version + RUNNING` 条件更新，失败后只读回当前状态，不重试写入。
- [清空和新运行创建并发] → 提交前只读取 `ACTIVE` 会话，最终占用条件同时要求 `ACTIVE` 与空 `active_run_id`；批量接口把失败项目记为跳过。
- [已运行的只读工具不能被线程安全地打断] → 保持其 `RUNNING` 状态，不增加未设计的中断机制；运行终态以保存后的取消事实为准。
- [逻辑清空的数据仍短暂存在] → 立即标记为可清理并从所有用户查询中排除，保留审计与既有异步清理顺序。
- [C 尚未接入新接口] → 同 change 提供固定 JSON 夹具和 Controller 测试；C 的页面联调不是 B 代码完成的前置条件。

## Migration Plan

1. 发布本 change 的 B 代码，不执行任何 Flyway 迁移。
2. 新接口上线后，C 可按夹具接入；旧 POST SSE 和运行查询保持不变。
3. 回退时撤销新增 Controller 路由和应用服务引用，不删除已经标记可清理的 Agent 数据，不修改 V008/V009。

## Open Questions

无。创建接口不接收标题，现有 `summary` 由后续运行时生成，避免把前端自由文本误当作可信摘要；如 C 后续需要会话命名，应另开 change 定义请求字段、长度和覆盖规则。
