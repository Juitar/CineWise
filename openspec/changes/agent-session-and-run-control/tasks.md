## 1. 会话查询与清空基础

- [x] 1.1 Owner：B；修改范围：Agent Repository 端口、MyBatis Mapper 和持久化实现，新增本人活动会话/消息分页查询、总数、逻辑清空和到期标记 SQL；验证：`AgentSessionPersistenceIntegrationTest` 覆盖用户隔离、排序、分页和条件更新。
- [x] 1.2 Owner：B；修改范围：`agent/application/persistence`，实现创建、会话列表、历史消息、单个清空和批量清空应用用例，只从 `CurrentUserAccessor` 获取身份；验证：`AgentSessionManagementServiceTest` 覆盖正常、空列表、越权/已清空资源不存在、活动运行 `206008`、批量跳过和查询无副作用。
- [x] 1.3 Owner：B；修改范围：既有 POST SSE 初始运行事务和会话占用条件 SQL，已清空会话按 `206005` 拒绝且不写入运行、消息或事件；验证：本地 `AgentInitialRunTransactionTest`、`AgentSessionPersistenceIntegrationTest` 通过；CI MySQL `AgentPersistenceMySqlIntegrationTest` 覆盖清空后提交和最终条件更新。

## 2. 运行取消

- [x] 2.1 Owner：B；修改范围：Agent 步骤/运行状态转换和应用服务，增加只跳过 `PENDING` 节点的取消规则、运行 `CANCELLED` CAS、活动运行引用释放和 `run.complete` 事件；验证：`AgentRunCancellationServiceTest` 覆盖完成/运行中/待开始混合步骤、重复取消、终态取消、越权资源不存在和不重复事件。
- [x] 2.2 Owner：B；修改范围：Agent MySQL 持久化集成测试，验证取消与清空/完成竞争时的条件更新与恢复结果；验证：GitHub Actions `Backend MySQL Integration` #114 通过，确认不会留下悬空 `active_run_id`，也不会把终态改回运行中。

## 3. HTTP 契约、夹具与测试

- [x] 3.1 Owner：B；修改范围：`agent/api` DTO、`AgentInteractionRuntimeService` 和 `AgentController`，增加 6 个会话管理/运行取消路由及统一 `Result`/`PageResult` 映射；验证：MockMvc 覆盖正常响应、分页参数、路径参数、`206005`、`206008` 和未认证 `201006`。
- [x] 3.2 Owner：B；修改范围：`backend/src/test/resources/fixtures/agent/c` 与夹具契约测试，新增会话创建、列表、历史、清空、批量清空、取消和已终态取消 JSON 样例；验证：夹具 JSON 与 DTO 字段名、字符串 ID、数值错误码的契约测试通过。

## 4. 交付自查

- [x] 4.1 Owner：B；修改范围：本 change 的 OpenSpec 任务状态和交付说明；验证：`openspec validate agent-session-and-run-control --strict` 通过，且仅勾选已实现并验证的任务。
- [x] 4.2 Owner：B；修改范围：本 change 的后端代码、测试和夹具；验证：H2 持久化集成测试、相关 Agent 单元/Controller/恢复测试与 `backend/mvnw.cmd verify` 通过；MySQL 8.4 并发测试保留在 2.2。
- [x] 4.3 Owner：B；修改范围：工作区检查；验证：`git diff --check`、`git status --short --branch` 和变更文件范围核对通过，不提交、不推送、不创建 PR。
