## 1. 前置确认与数据方案

- [x] 1.1 Owner B：补齐 `active_run_id` 的 `BIGINT NULL`、逻辑关联 `agent_run.id`、正数 CHECK、索引，以及“插入运行与条件占用同事务、终态按当前 run 条件释放”的完整规则；同步总体设计。
- [x] 1.2 Owner B：补齐四表完整字段、状态 CHECK、唯一键、查询索引、`expire_at` 索引和 `agent_run_step → agent_message → agent_run → agent_session` 的 30 天清理顺序；核对本 Change 不包含 `agent_event`、`agent_action`、`agent_feedback`、`agent_tool_call`。
- [x] 1.3 Owner B：将完整 Change 推送到可审查分支并执行严格校验，向 A 提供复核材料；最新已推送提交为 `c9e85ab`，`openspec validate agent-session-run-persistence --strict` 已通过。
- [x] 1.4 Owner A：已复核 Change 与总体设计，正式分配 `V008__create_agent_session_run_tables.sql`；本授权仅允许 B 创建本地迁移草案并交 A 静态审查，不允许执行、提交或推送。
- [x] 1.5 Owner C：已确认 Agent Controller 与 Application Service 统一经 `CurrentUserAccessor` 获取当前用户；未认证直接复用 `401 / 201006 / SESSION_INVALID`，不从请求体、SSE 参数或 Agent DTO 读取可信 userId，不新建 Agent 私有身份错误码。

## 1.4 复核补充

- [x] 1.6 Owner B：把 Agent 详细设计第 5.2.1 节设为本 Change 的唯一四表字段来源，逐字段列出类型、长度、可空性、默认值、CHECK、唯一键和索引，并标明未来完整模型不适用于本次迁移。
- [x] 1.7 Owner B：冻结 session/run/message/step 的状态、角色、消息类型、终态时间和组合关系；定义步骤 `version` CAS、RUNNING/PROCESSING、崩溃超时恢复、到期清理和 `request_hash v1`。
- [x] 1.8 Owner A：已根据小修后的复核材料正式分配本 Change 的 `V008__create_agent_session_run_tables.sql`；版本分配仅允许形成迁移草案，不代表执行、提交或推送授权。
- [x] 1.9 Owner B：小修已提交并推送：CineWise 为 `c9e85ab`，CineWise-Docs 为 `c921ff2`；`openspec validate agent-session-run-persistence --strict` 已通过，并已据此完成最终复核。
- [x] 1.10 Owner B：已实现启动/新消息提交前的陈旧运行恢复，并补充 30 秒阈值、CAS 冲突、失败落库、条件释放 `active_run_id`、统一运行保留时间及已清空会话不得保留活动运行的测试；不重放模型或工具。

## 2. Agent 持久化模型与迁移

- [x] 2.1 Owner B：已新增 B 自有的会话、运行、消息、步骤领域模型和 Repository 端口，明确运行状态、消息状态、计划/槽位快照和脱敏 JSON 边界；`AgentPersistenceModelTest` 与 `AgentPersistenceBoundaryTest` 共 5 项测试通过，验证未引入 A/C/D 的持久化类型。
- [x] 2.2 Owner B：已在 A 分配 V008 后创建本地 Agent 表迁移、四个持久化 Entity、参数绑定 Mapper 和 Repository 实现；使用全局 ID、`DATETIME(3)`、状态/正数 CHECK、唯一请求索引、会话活动运行条件更新与步骤 version CAS。`AgentPersistenceModelTest`、`AgentPersistenceBoundaryTest`、编译和 Checkstyle 通过；B 不连接云端迁移验证库，真实 Agent 集成验证改由本 Change 的 CI 一次性 MySQL 8.4 容器执行。
- [x] 2.2.1 Owner B：A 已完成 `V008__create_agent_session_run_tables.sql` 的 SQL 静态审查，并冻结 SHA-256 为 `41E38D53F00C68A82E63F51847E7A27525B68336B176A68592A4990D871E1797`；SQL 不得改动。A 已完成 V001–V008 空 MySQL 8.4 验证；B 不执行或连接共享库，后续只在 CI 临时库运行 Agent 持久化测试。
- [x] 2.3 Owner B：已实现按 `userId + sessionId/runId` 过滤的会话和运行读取，以及对 `user_id + session_id + client_request_id` 的重复请求查询与请求摘要一致性校验；`AgentSessionQueryServiceTest`、`AgentInitialRunTransactionTest` 验证越权隐藏、摘要冲突和唯一键并发回读。
- [x] 2.3.1 Owner B：已实现并测试 `request_hash v1` 的固定 JSON 字段顺序、Unicode NFC、换行统一、Unicode 码点排序、UTF-8 SHA-256 小写十六进制和规范化后重复槽位键拒绝；当前仍未完成按当前用户的读取和摘要冲突用例。
- [x] 2.3.2 Owner B：已实现 `AgentSessionQueryService`，只经 `CurrentUserAccessor` 取得用户 ID，并使 session/run 查询从 Repository 起即带 `userId` 条件；`AgentSessionQueryServiceTest` 验证本人读取与越权隐藏为 404。摘要冲突用例仍随消息提交事务完成。

## 3. 最小只读运行持久化用例

- [x] 3.1 Owner B：已实现会话创建和最小只读消息提交 Application Service；用户身份只来自 `CurrentUserAccessor`，活动运行位由条件更新占用，失败返回 `206008`。
- [x] 3.2 Owner B：已实现“初始短事务 → 事务外 MinimalReadOnlyAgentService → 结果短事务”；保存用户消息、已校验计划/步骤和受控结构化回复，D 工具不处于数据库事务中。
- [x] 3.3 Owner B：已实现重复 `clientRequestId` 返回既有持久化快照、`206009` 摘要冲突、终态条件释放和 `PROCESSING` 保持 `RUNNING`；测试验证重复请求不会再次调用主控或 D 工具。
- [x] 3.4 Owner B：已为主控/工具异常和不完整计划保存 `FAILED` 与安全 ERROR 回复；步骤只存已校验依赖、输入引用和已引用槽位快照，不保存模型原文、异常对象、认证秘密、精确位置或完整工具响应。

## 4. 测试与验证

- [x] 4.1 Owner B：已补充 Repository/Application 单元测试，覆盖会话归属、越权隐藏、重复请求、摘要冲突、单会话活动运行、失败终态、QUESTION/推荐路径的持久化规则和 PROCESSING 状态。
- [ ] 4.2 Owner B：已把 `AgentPersistenceMySqlIntegrationTest` 和 `backend-mysql-integration.yml` 改为 CI 一次性 MySQL 8.4 方案：保留票务库，在同一容器创建 `cinewise_agent_it` 并只授权 `cinewise_ci`；测试只允许 `CINEWISE_MYSQL_AGENT_PERSISTENCE_IT=true` 且数据源精确为 `cinewise_agent_it / cinewise_ci` 时执行。CI 分两次启动该测试类，分别验证空库首次 Flyway 初始化、V008 历史记录、数据库唯一约束、条件更新竞争、工具调用不占事务和重复初始化；不连接或请求云端 `cinewise_migration_check` 凭据。待工作流实际通过后勾选。
- [x] 4.3 Owner B：`openspec validate agent-session-run-persistence --strict`、`git diff --check` 和 `backend/mvnw.cmd --batch-mode --no-transfer-progress verify` 均以退出码 0 通过；Surefire 共 183 项，0 失败、0 错误、13 项跳过（包括未连接本机 MySQL 的受安全开关保护用例）。完整验证已完成打包、Checkstyle、SpotBugs 和 JaCoCo；V008 SQL 哈希仍为 `41E38D53F00C68A82E63F51847E7A27525B68336B176A68592A4990D871E1797`。CI 的 Agent MySQL 实测仍由未完成的 4.2 记录，SSE/确认动作不在本 Change。
