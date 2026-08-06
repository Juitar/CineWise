## 1. OpenSpec 与模型网关（B）

- [x] 1.1 新增 DeepSeek 属性、HTTP 适配器和 JSON 计划/回复解析；验证 `DeepSeekModelGatewayTest` 不泄露密钥且正确映射成功、HTTP、超时和无效 JSON。
- [x] 1.2 调整 Spring 装配：默认 Mock，显式完整配置时使用真实模型；验证 `AgentToolConfigurationTest`。

## 2. 多工具核心执行（B）

- [x] 2.1 新增类型化 `AgentToolExecutor` 注册接口和启动校验，将 `rankMoviePlan` 迁移为该接口；验证注册表、类型不符和未知工具测试。
- [x] 2.2 完成多个只读工具按状态机顺序执行、`tool.start`、`tool.complete`、`tool.error` 事实和安全结果映射；下游未预期异常统一为不可重试的内部错误 `ToolResult`，不写入异常原文；验证 `MultiToolSupervisorTest`、`RankMoviePlanExecutionAdapterTest`。
- [x] 2.3 实现只读失败建议后的有上限重规划，并保持原运行、请求标识和 CAS 保存要求；验证重规划和不重放节点测试。
- [x] 2.4 复核确认后的 `createOrder` 只经既有确认服务调用，结果未知只查询原 actionId/幂等键；验证现有确认建单测试。

## 3. 未确认业务工具准备（B，等待 A/D 正式 API）

- [x] 3.1 为 `queryAvailableDates`、`queryShows`、`querySeats` 提供无业务字段猜测的注册 SPI、Mock executor 和夹具；验证生产装配不暴露未接入工具。`querySeats` 仅保留扩展位，不作为本 change 验收依赖。
- [x] 3.2 已接入 A 合入 `dev` 的 `queryAvailableDates`、`queryShows` 类型化 Adapter；Command 只接受正十进制 `cinemaId`，`queryShows` 强制 `businessDate`，A 的成功结果提供 `dataAt`/`expiresAt`，Adapter 缺失时效会统一返回 `QUERY_UNAVAILABLE/REFRESH_REQUIRED`。验证：`TicketingReadToolExecutionAdapterTest`、`AgentToolConfigurationTest`。
- [x] 3.3 B 已切换 `rankMoviePlan` 到 D 的 `RecommendationPlanResult` / `RecommendationPlan` 公开 Tool 入口；回复映射保留已确认的影片/影院 ID、场次、票价、开始时间、来源、时效和降级事实，并用 B 夹具覆盖成功、空结果、降级和过期结果。验证：`RecommendationReplyFactsMapperTest`、`MinimalReadOnlyAgentServiceTest`、`RankMoviePlanExecutionAdapterTest`。
- [x] 3.4 D 已在 `dev` 提供成功、空结果、降级夹具和可空展示字段规则；B 已把 `rankMoviePlan` 白名单与 Adapter 切换为完整 Command：`cityCode/date/ticketCount` 必填，`genres/excludedGenres` 使用 JSON 数组文本。当前 C 的 `PLAN_CARD` 夹具只定义已映射字段，B 不擅自增加影片名称、影院名称、评分、距离或预计路程字段。验证：`RankMoviePlanToolTest`、`RankMoviePlanExecutionAdapterTest`。

## 4. SSE 回复映射（B；前端夹具待 C）

- [x] 4.1 补齐 `QUESTION`、`PLAN_CARD`、`PROGRESS`、`ERROR`、工具阶段事件，以及 `card` + `BUSINESS_INTENT/SELECT_SEATS` 的持久化映射；验证 SSE 映射测试。
- [x] 4.2 C 的前端固定夹具已合入；B 已用后端 `AgentCFixtureContractTest` 和 SSE 持久化测试核对字段，并在 Node `v24.19.0` 环境运行前端 Vitest：89 个测试文件、426 项全部通过。
- [x] 4.3 已根据 C 确认的统一外层字段、`businessRef.showId` 和工具事件字段调整 B 侧协议校验；前端投影测试仍由 C 负责。
- [x] 4.4 修复 `SELECT_SEATS` 的 `businessRef`：强制输出并校验 `showId`、`movieId`、`cinemaId` 三个正 `long` 十进制字符串；更新 B 的持久化映射、夹具与定向测试。验证：`SelectSeatsReplyFactsTest`、`AgentPersistenceJsonFactoryTest`、`AgentCardEventValidatorTest`、`AgentRunResultTransactionTest`。

## 5. 验证与交付（B）

- [x] 5.1 运行 Agent 目录下 42 个非 MySQL 集成测试类，共 193 项通过、0 失败、0 跳过；命令为 `backend/mvnw.cmd "-Dtest=<42 个 Agent 测试类>" test`，并设置 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 绕过本机 JDK 21 的 C2 崩溃。
- [x] 5.2 更新已完成任务，执行 `openspec validate agent-acceptance-critical-path --strict` 和 `git diff --check`。
- [x] 5.3 所有可独立任务完成、定向测试通过且准备提交时，执行 `backend/mvnw.cmd verify`；本 change 不涉及 MySQL、迁移、锁或并发，无需单独 MySQL 8.4 CI 结论。
