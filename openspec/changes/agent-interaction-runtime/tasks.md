## 1. 已确定边界与迁移前置条件

- [ ] 1.1 A 复核完整 OpenSpec 中的事件顺序、V008 UUID 格式、不可变性、载荷上限、索引与清理规则后，正式分配 V009 并审查最终 SQL；验证：记录 V009 分配和 SQL 静态审查结论，V008 无任何改动。
- [x] 1.2 B 核对 C 的已定 POST SSE 请求、固定事件 JSON、十进制 `Last-Event-ID`、重复事件去重和 `stream.reset` 后运行详情重建规则；验证：前端设计已覆盖该契约，C 的夹具消费移至联调证据。
- [x] 1.3 B 核对 D 的既有范围：只读取 `rankMoviePlan` 已校验摘要，不新增 D 调用、不刷新快照、不创建任务或请求位置、路线、餐饮；验证：本 Change 不引入 D 的新 API 或写操作。

## 2. 事件持久化与查询模型

- [ ] 2.1 在 A 分配 V009 后新增 `agent_event` 前向迁移、Entity、Mapper、Repository 和领域模型；验证：迁移不修改 V008，使用 V008 的 `VARCHAR(36)` UUID，包含会话/运行/过期索引、不可变记录约束和 `expire_at >= create_time` 检查。
- [ ] 2.2 实现事件记录和重放查询服务，确保运行/消息/步骤事实与对应事件在同一短事务保存；验证：单元测试覆盖同一会话读取与提交交错时只读到已提交前缀、下一次续传不漏事件、载荷超限/敏感字段拒绝且 SSE 不可见。
- [ ] 2.3 实现按当前用户、会话和十进制游标查询事件，以及按 runId 查询最后事件水位线；验证：单元测试覆盖正常续传、跨会话游标、已清理游标、`stream.reset` 水位线和 `idx_agent_event_run_event` 查询。
- [ ] 2.4 扩展运行详情装配 DTO，返回稳定的运行、消息、步骤、`lastEventId` 和安全恢复提示；验证：查询自己成功、越权资源不存在、查询没有任何副作用。

## 3. Agent 交互接口

- [ ] 3.1 新增 Agent HTTP 请求/响应 DTO 和 Controller，只从 `CurrentUserAccessor` 取得用户，并保留 C 的未认证错误语义；验证：Web MVC 测试覆盖 401/201006、参数校验和不接受 `userId`。
- [ ] 3.2 将现有最小只读提交路径接入事件记录，输出与真实持久化状态对应的固定事件；验证：测试覆盖完成、失败和 `PROCESSING` 运行，且不伪造模型增量或敏感载荷。
- [ ] 3.3 实现 POST SSE 写出、心跳和同请求幂等重放；验证：Web MVC/集成测试覆盖首发、相同 `clientRequestId` 重放、重复事件 ID 与不重复调用 `rankMoviePlan`。
- [ ] 3.4 实现 `GET /api/v1/agent/runs/{runId}`；验证：接口测试覆盖本人 `RUNNING`、终态、失败结果和其他用户 runId 不泄露。

## 4. 夹具、集成验证与交付检查

- [ ] 4.1 新增 C 可消费的 SSE 和运行详情 JSON 夹具；验证：至少覆盖推荐卡、`PROCESSING`、失败、重复事件和 `stream.reset` 五种场景。
- [ ] 4.2 扩展 Agent MySQL 8.4 临时库集成测试；验证：在 `cinewise_agent_it` 验证 V009 新迁移、事件顺序、并发读取不漏事件、断线续传、运行轨迹索引、30 天清理和重复请求不重复执行；A 的 MySQL 8.4 迁移验证另行执行，CI 不替代它。
- [ ] 4.3 执行 Agent 单元/接口/集成测试及 `backend/mvnw.cmd verify`；验证：记录实际通过、失败、跳过数和环境限制。
- [ ] 4.4 执行 `openspec validate agent-interaction-runtime --strict`、`git diff --check`、`git status`，并把 C 的夹具联调结果和未验证项写入交付说明；验证：严格校验通过且不含 V008 修改或无关文件。
