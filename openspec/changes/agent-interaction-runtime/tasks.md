## 1. 已确定边界与迁移前置条件

- [x] 1.1 A 已复核完整 OpenSpec 并正式分配 V009；实际进入 `origin/dev` 的 V009 迁移为 `cd85247`，SHA-256 为 `A4C94FF78EB53FF83BBD23ECE0A0CA75231B7627B4065BD362520EE9B01FA1B9`；验证：V008 无任何改动，迁移验证与日常库执行由 A 负责。
- [x] 1.2 B 核对 C 的已定 POST SSE 请求、固定事件 JSON、十进制 `Last-Event-ID`、重复事件去重和 `stream.reset` 后运行详情重建规则；验证：前端设计已覆盖该契约，C 的夹具消费移至联调证据。
- [x] 1.3 B 核对 D 的既有范围：只读取 `rankMoviePlan` 已校验摘要，不新增 D 调用、不刷新快照、不创建任务或请求位置、路线、餐饮；验证：本 Change 不引入 D 的新 API 或写操作。

## 2. 事件持久化与查询模型

- [x] 2.1 在 A 分配 V009 后新增 `agent_event`、`agent_event_stream_cursor` 前向迁移、Entity、Mapper、Repository 和领域模型；验证：迁移不修改 V008，使用 V008 的 `VARCHAR(36)` UUID，包含会话/运行/过期索引、持久化事件类型 CHECK、不可变事件、可更新游标和 `expire_at >= create_time` 检查。
- [x] 2.2 实现事件记录、会话行锁和重放查询服务，确保运行/消息/步骤事实、事件和游标水位线在同一短事务保存；验证：GitHub Actions `Backend MySQL Integration` #98 在一次性 MySQL 8.4 `cinewise_agent_it` 通过，覆盖同会话锁等待、提交顺序和同请求并发；载荷超限/敏感字段回滚及安全失败由 Agent 单元测试覆盖。
- [x] 2.3 实现按当前用户、会话和十进制游标查询事件，以及按 runId 查询最后事件水位线；验证：单元测试覆盖无请求头与 `Last-Event-ID: 0` 的保留事件读取、两者在无事件时不发送 `stream.reset`、正常正整数续传、已清理、跨会话、正常全局空洞、未来游标、`stream.reset` 水位线和 `idx_agent_event_run_event` 查询；MySQL 执行随 4.2 保留。
- [x] 2.4 扩展运行详情装配 DTO，返回稳定的运行、消息、步骤、`lastEventId` 和安全恢复提示；验证：查询自己成功、越权资源不存在、查询没有任何副作用。

## 3. Agent 交互接口

- [x] 3.1 新增 Agent HTTP 请求/响应 DTO 和 Controller，只从 `CurrentUserAccessor` 取得用户，并保留 C 的未认证错误语义；验证：Web MVC 测试覆盖 401/201006、参数校验和不接受 `userId`。
- [x] 3.2 将现有最小只读提交路径接入事件记录，输出与真实持久化状态对应的固定事件；验证：测试覆盖完成、失败和 `PROCESSING` 运行，且不伪造模型增量或敏感载荷。
- [x] 3.3 实现 POST SSE 写出、心跳和同请求幂等重放；验证：Web MVC/集成测试覆盖首发、相同 `clientRequestId` 重放、重复事件 ID 与不重复调用 `rankMoviePlan`。
- [x] 3.4 实现 `GET /api/v1/agent/runs/{runId}`；验证：接口测试覆盖本人 `RUNNING`、终态、失败结果和其他用户 runId 不泄露。

## 4. 夹具、集成验证与交付检查

- [x] 4.1 新增 C 可消费的 SSE 和运行详情 JSON 夹具；验证：至少覆盖推荐卡、`PROCESSING`、失败、重复事件和 `stream.reset` 五种场景。
- [x] 4.2 扩展 Agent MySQL 8.4 临时库集成测试；验证：GitHub Actions `Backend MySQL Integration` #98 在 `cinewise_agent_it` 通过，确认 V009、同会话行锁、提交顺序、同请求并发、统一到期时间和事件游标更新；游标起始、无效游标、断线续传、轨迹索引、终态保护与清理顺序由对应单元/接口测试覆盖。A 的 MySQL 8.4 迁移验证另行执行，CI 不替代它。
- [x] 4.3 执行 Agent 单元/接口/集成测试及 `backend/mvnw.cmd verify`；验证：本地 `backend\\mvnw.cmd verify` 通过（285 tests，0 failures，0 errors，16 skipped）；本 PR 最新提交的 Backend Verify、Backend MySQL Integration、Backend Redis Integration 与 Frontend Verify 均通过。
- [x] 4.4 执行 `openspec validate agent-interaction-runtime --strict`、`git diff --check`、`git status`，并把 C 的夹具联调结果和未验证项写入交付说明；验证：C 在 PR #53 评论确认字符串 ID、重复事件去重、安全错误和重置恢复流程；其指出的 `traceId`、可区分事件 ID 与非空恢复快照已在 `b1e93d7` 修正。真实推荐卡字段仍是 D 的后续只读摘要范围，本 Change 保持安全占位，不把未确认字段写入载荷。
- [ ] 4.5 修正 `stream.reset` 会话水位线续传规则；验证：B 夹具断言重置事件 `eventId` 与 `payload.watermark` 一致，C 先 GET 重建后以该值作为下一次 `Last-Event-ID`，不会因运行级 `lastEventId` 较小而循环重置。
