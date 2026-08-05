## 1. OpenSpec 与依赖确认

- [x] 1.1 B：依据现有 Agent 运行、SSE、A 建单和 C 前端设计完成 proposal、spec、design、tasks 并严格校验；验证：`openspec validate agent-confirmed-order-execution --strict`。
- [x] 1.2 A：确认 Agent 专用创建订单公开 Application API/类型化 Tool、DTO、调用身份、`actionId` 校验、稳定幂等键、结果查询、错误码、事务边界和结果未知语义；验证：A 书面确认与契约测试。
- [x] 1.3 A：确认 V011 已进入最新 `dev`，正式将 `agent_action` 分配为 V012；验证：A 的书面确认。
- [x] 1.4 A：审查并发布 `V012__create_agent_action_table.sql` 的字段表、索引、CHECK、生命周期和 MySQL 迁移验证；验证：`dev@ddd4fcf` 与 A 的书面审查。B 不修改已发布迁移。
- [x] 1.5 C：确认确认 REST/SSE 的展示错误码映射、CSRF 和“结果确认中/已失效”消费语义；验证：C 的消费者确认。本 change 不实现前端。

## 2. B 自有领域模型与状态规则

- [x] 2.1 B：实现 `AgentConfirmationAction`、`AgentConfirmationActionStatus`、`ConfirmedOrderCommand`、安全展示投影和不可变状态转换；验证：状态机单元测试覆盖创建、拒绝、过期、Claim、成功、失败、结果未知和禁止逆转。
- [x] 2.2 B：实现服务端确定性参数摘要，覆盖工具名、场次和排序座位，不接受用户/金额/订单状态/前端哈希；验证：摘要稳定性、座位排序、字段变化与非法输入测试。
- [x] 2.3 B：实现归属、运行状态、节点、计划版本、参数变化、有效期和业务候选校验规则；验证：越权、不存在、运行结束、版本变化、参数变化、过期和业务失效单元测试。
- [x] 2.4 B：实现稳定 `clientRequestId`/`idempotencyKey` 派生与恢复规则；验证：重复动作复用原键、结果未知不生成新键测试。

## 3. B 自有端口、Mock 与夹具

- [x] 3.1 B：定义 `AgentConfirmationActionRepository`、CAS Claim/完成保存端口和 `CreateOrderToolAdapter`、`ToolContext`、`ToolResult` 边界；验证：ArchUnit 或编译检查不依赖 A Controller/Entity/Mapper/Repository。
- [x] 3.2 B：实现内存 action Repository、A 建单 Mock、成功/业务失败/结果未知夹具；验证：Mock 调用计数和安全字段扫描测试。
- [x] 3.4 B：实现 C 已确认的公开 action 状态映射、确认卡 payload 和 `AgentActionResponse` DTO；验证：状态映射、payload 白名单、纯文本展示与 DTO 测试。
- [x] 3.3 B：实现确认应用服务的三段流程：短事务 Claim、事务外适配器、新短事务结果保存；验证：`AgentPersistenceMySqlIntegrationTest` 在 GitHub Actions `31025050412` 中确认适配器不在事务内，且 CAS 胜者唯一。

## 4. 持久化与确认接口

- [x] 4.1 B/A：`V012__create_agent_action_table.sql` 已由 A 静态审查、MySQL 8.4 迁移验证并发布；B 已实现 MyBatis 映射、查询索引、唯一约束和 CAS SQL；验证：`dev@ddd4fcf`、`AgentConfirmationActionPersistenceTest`、GitHub Actions `31025050412`。
- [ ] 4.2 B：在持久化实现可用后实现 `POST /api/v1/agent/actions/{actionId}/confirm`，只接受 `{confirmed}` 且经 `CurrentUserAccessor` 校验；验证：Controller/Service 成功、拒绝、越权、缺失、过期、206003、206004、206006、运行结束和版本变化测试。
- [ ] 4.3 A/B：A 在独立 change 落地 `com.miaoyu.ticket.order.api.CreateOrderTool`、`CreateOrderForAgentCommand`、`AgentOrderResult` 和原请求查询入口后，B 实现并注册生产 `CreateOrderToolAdapter`；验证：只经 A 公开 API 的契约测试、原键结果查询和无本机 HTTP/私有持久化访问扫描。
- [ ] 4.4 A：在 `CreateOrderTool.execute` 调用 `OrderApplicationService` 前调用 B 的 `AgentActionAuthorizationPort`；验证：A 的 Tool 契约测试授权拒绝返回 `205004` 且订单不落库。当前 `dev` 的 `CreateOrderTool` 尚未引用该 Port，B 不得以本模块本地校验替代。

## 5. SSE、恢复与并发

- [ ] 5.1 B：在持久化事件可用后发布安全确认卡和结果事件，先保存事实后回放；验证：payload 白名单、旧卡失效、重复事件和断线重连测试。
- [ ] 5.2 B：实现结果未知恢复器，只按原键查询 A；验证：超时、断线、SSE 断开、进程恢复、查询明确成功/失败和查询无结论测试，确认不自动重发。
- [x] 5.3 B：用真实持久化验证同 action 并发确认、CAS 冲突、胜者读取、终态不可重入和事务回滚不留半成品；验证：GitHub Actions MySQL 8.4 `AgentPersistenceMySqlIntegrationTest`，运行 `31025050412`（第 151 次）通过。

## 6. MySQL CI 与交付检查

- [x] 6.1 B/A：已在 `Backend MySQL Integration / mysql-integration` 的一次性 MySQL 8.4 `cinewise_agent_it` 执行空库 Flyway、重复启动、action 创建/查询/过期/重复确认/并发/CAS/未知恢复/回滚；运行 `31025050412`（第 151 次）通过。
- [ ] 6.2 B：完成本地相关单元和 H2 集成测试、`backend/mvnw.cmd verify`、严格 OpenSpec 校验、`git diff --check`、状态和变更范围核对；验证：记录实际输出和未验证项。
