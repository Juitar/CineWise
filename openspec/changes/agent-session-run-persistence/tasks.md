## 1. 前置确认与数据方案

- [x] 1.1 Owner B：补齐 `active_run_id` 的 `BIGINT NULL`、逻辑关联 `agent_run.id`、正数 CHECK、索引，以及“插入运行与条件占用同事务、终态按当前 run 条件释放”的完整规则；同步总体设计。
- [x] 1.2 Owner B：补齐四表完整字段、状态 CHECK、唯一键、查询索引、`expire_at` 索引和 `agent_run_step → agent_message → agent_run → agent_session` 的 30 天清理顺序；核对本 Change 不包含 `agent_event`、`agent_action`、`agent_feedback`、`agent_tool_call`。
- [x] 1.3 Owner B：将完整 Change 推送到可审查分支并执行严格校验，向 A 提供复核材料；V008 在此之前仅为候选版本。最新已推送提交为 `8e14726`，`openspec validate agent-session-run-persistence --strict` 已通过。
- [ ] 1.4 Owner A：复核已推送 Change 与总体设计后，正式分配 V008；版本分配不等于 SQL 执行或共享库发布授权。
- [ ] 1.5 Owner C：确认 `CurrentUserAccessor` 的未认证异常可由 B 的 Application 用例直接复用；本次不新增 HTTP 或 SSE 协议。

## 1.4 复核补充

- [x] 1.6 Owner B：把 Agent 详细设计第 5.2.1 节设为本 Change 的唯一四表字段来源，逐字段列出类型、长度、可空性、默认值、CHECK、唯一键和索引，并标明未来完整模型不适用于本次迁移。
- [x] 1.7 Owner B：冻结 session/run/message/step 的状态、角色、消息类型、终态时间和组合关系；定义步骤 `version` CAS、RUNNING/PROCESSING、崩溃超时恢复、到期清理和 `request_hash v1`。
- [ ] 1.8 Owner A：根据本轮小修后的复核材料正式分配本 Change 的 V008；版本分配仅允许形成迁移草案，不代表执行、提交或推送授权。
- [ ] 1.9 Owner B：本轮小修仅保留本地、未提交未推送；按 A 的后续授权记录提交号和 `openspec validate agent-session-run-persistence --strict` 结果，再发起最终复核。
- [ ] 1.10 Owner B：补充陈旧运行恢复测试，覆盖启动/新消息提交、30 秒阈值边界、CAS 冲突、失败落库、条件释放 `active_run_id`，以及运行/消息/步骤统一到期时间和会话删除前置条件。

## 2. Agent 持久化模型与迁移

- [ ] 2.1 Owner B：新增 B 自有的会话、运行、消息、步骤领域模型和 Repository 端口，明确运行状态、消息状态、计划/槽位快照和脱敏 JSON 边界；验证不引入 A/C/D 的持久化类型。
- [ ] 2.2 Owner B：在 A 正式分配 V008 并授权后创建 Agent 表迁移、Entity、Mapper 和 Repository 实现，使用全局 ID、`DATETIME(3)`、状态/正数 CHECK、唯一请求索引和会话活动运行条件更新；在空 MySQL 验证前不启用迁移。
- [ ] 2.3 Owner B：实现按 `userId + sessionId/runId` 过滤的会话和运行读取，以及对 `user_id + session_id + client_request_id` 的重复请求查询与请求摘要一致性校验；验证越权查询不泄露任何记录。

## 3. 最小只读运行持久化用例

- [ ] 3.1 Owner B：实现会话创建和最小只读消息提交 Application Service，从 `CurrentUserAccessor` 取得当前用户，并以数据库条件更新取得或拒绝会话活动运行位；验证同会话不同请求在活动运行时返回 `206008`。
- [ ] 3.2 Owner B：实现“初始运行短事务 → 事务外调用 MinimalReadOnlyAgentService → 结果短事务”的提交流程；保存用户消息、已校验计划/步骤和结构化回复，且不把 D 工具调用包进事务。
- [ ] 3.3 Owner B：实现重复 `clientRequestId` 返回既有运行、摘要不一致拒绝、终态释放活动运行位和 `PROCESSING` 保持运行位的规则；验证重复请求不再次调用 D 的推荐工具。
- [ ] 3.4 Owner B：为失败、异常和不完整计划保存稳定运行/步骤状态与安全回复；验证不保存模型原始输出、异常对象、认证秘密、精确位置或完整工具响应。

## 4. 测试与验证

- [ ] 4.1 Owner B：补充 Repository/Application 单元测试，覆盖会话归属、越权隐藏、重复请求、摘要冲突、单会话活动运行、QUESTION/推荐/失败终态和 PROCESSING 状态。
- [ ] 4.2 Owner B：在 A 授权后补充最小集成测试，覆盖数据库唯一约束、条件更新竞争和两段事务中工具调用不占用事务；在空 MySQL 执行迁移和重复初始化验证。
- [ ] 4.3 Owner B：执行 `openspec validate agent-session-run-persistence --strict`、`backend/mvnw.cmd verify`、`git diff --check`，并记录实际测试结果、A 的迁移确认和未实现的 SSE/确认/恢复范围。
