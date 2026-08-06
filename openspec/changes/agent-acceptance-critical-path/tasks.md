## 1. OpenSpec 与模型网关（B）

- [x] 1.1 新增 DeepSeek 属性、HTTP 适配器和 JSON 计划/回复解析；验证 `DeepSeekModelGatewayTest` 不泄露密钥且正确映射成功、HTTP、超时和无效 JSON。
- [x] 1.2 调整 Spring 装配：默认 Mock，显式完整配置时使用真实模型；验证 `AgentToolConfigurationTest`。

## 2. 多工具核心执行（B）

- [x] 2.1 新增类型化 `AgentToolExecutor` 注册接口和启动校验，将 `rankMoviePlan` 迁移为该接口；验证注册表、类型不符和未知工具测试。
- [x] 2.2 完成多个只读工具按状态机顺序执行、`tool.start`、`tool.complete`、`tool.error` 事实和安全结果映射；验证 `MultiToolSupervisorTest`。
- [x] 2.3 实现只读失败建议后的有上限重规划，并保持原运行、请求标识和 CAS 保存要求；验证重规划和不重放节点测试。
- [x] 2.4 复核确认后的 `createOrder` 只经既有确认服务调用，结果未知只查询原 actionId/幂等键；验证现有确认建单测试。

## 3. 未确认业务工具准备（B，等待 A/D 正式 API）

- [x] 3.1 为 `queryAvailableDates`、`queryShows`、`querySeats` 提供无业务字段猜测的注册 SPI、Mock executor 和夹具；验证生产装配不暴露未接入工具。`querySeats` 仅保留扩展位，不作为本 change 验收依赖。
- [ ] 3.2 已接入 A 合入 `dev` 的 `queryAvailableDates`、`queryShows` 类型化 Adapter；Agent Command 遵守有效 `cinemaId` 和 `businessDate` 必填规则。等待 A 为两个成功 `ToolResult` 补齐 `dataAt`、`expiresAt` 后做动态时效联调。
- [ ] 3.3 等待 D 完成 `RecommendationPlanResult` / `RecommendationPlan` DTO 与 `RankMoviePlanTool` 返回类型升级，补齐影片名称、影院名称、评分、距离、预计路程的空值、脱敏和降级规则，并提供成功、空结果、降级三套夹具；随后更新 `rankMoviePlan` 结果卡片映射和夹具。D 已确认当前结果不能作为 `PLAN_CARD` 依据，B 暂不适配。

## 4. SSE 回复映射（B；前端夹具待 C）

- [x] 4.1 补齐 `QUESTION`、`PLAN_CARD`、`PROGRESS`、`ERROR`、工具阶段事件，以及 `card` + `BUSINESS_INTENT/SELECT_SEATS` 的持久化映射；验证 SSE 映射测试。
- [ ] 4.2 等待 C 提供前端固定夹具后，执行 `tool.complete`、`tool.error`、`SELECT_SEATS` 卡片的前后端联调。
- [x] 4.3 已根据 C 确认的统一外层字段、`businessRef.showId` 和工具事件字段调整 B 侧协议校验；前端投影测试仍由 C 负责。

## 5. 验证与交付（B）

- [x] 5.1 运行全部 Agent 定向单元测试并记录结果。
- [x] 5.2 更新已完成任务，执行 `openspec validate agent-acceptance-critical-path --strict` 和 `git diff --check`。
- [ ] 5.3 所有可独立任务完成、定向测试通过且准备提交时，执行 `backend/mvnw.cmd verify`；本 change 不涉及 MySQL、迁移、锁或并发，无需单独 MySQL 8.4 CI 结论。
