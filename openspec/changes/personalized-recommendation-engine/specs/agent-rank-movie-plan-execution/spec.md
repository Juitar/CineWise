## MODIFIED Requirements

### Requirement: rankMoviePlan 必须作为只读类型化推荐工具登记

系统 SHALL 在 B 的 Agent 工具白名单中以精确名称 `rankMoviePlan` 登记一个工具定义。该定义 MUST 使用 `RankMoviePlanCommand` 作为 Command 类型、`RecommendationPlanResult` 作为结果类型、`readOnly=true`、3 秒超时和 `idempotencyRequired=false`。输入必须包含 `cityCode:String`、`date:LocalDate`、`ticketCount:Integer`；可选字段为 `movieId:String`、`cinemaId:String`、`genres:List<String>`、成对的 `timeFrom/timeTo:LocalTime`、`latestEndTime:LocalTime`、`budget:BigDecimal`、`excludedTags:List<String>`、`maxDistanceMeters:Integer`。`genres` 与 `excludedTags` 必须使用受控 JSON 数组传递，不得逗号拼接；本 change 中 `excludedTags` 只允许影片类型。此前 B 确认的 `mood` 字段不再由 D 使用，需由 B 在实现前从白名单 DTO 删除。不得接收 `showId`、`price`、`seatIds`、库存、精确位置或不可信 `userId`。

#### Scenario: 校验合法的完整推荐节点
- **WHEN** 已注册白名单的候选计划节点引用 `rankMoviePlan`，并以匹配类型的槽位提供城市、日期、张数及可选约束
- **THEN** `PlanSchemaValidator` 接受该只读工具节点
- **AND** 生成的运行节点保持服务端初始状态，不执行 D 工具

#### Scenario: 拒绝不安全或不完整输入
- **WHEN** 节点缺少城市、日期或张数，提供不成对时段、非法预算或距离，或者输入 `showId`、价格、座位、库存、精确位置或 `userId`
- **THEN** `PlanSchemaValidator` 按既有结构化问题拒绝计划
- **AND** 系统不得按字符串查找 Bean、类或方法

### Requirement: 已校验节点必须通过唯一类型化入口调用 D

系统 SHALL 只为已校验、当前可开始的只读 `CALL_TOOL` 节点构造 `RankMoviePlanCommand`，并且 MUST 只调用 `RankMoviePlanTool.execute(ToolContext, RankMoviePlanCommand)`。工具上下文的运行标识和追踪标识 MUST 来自 B 的受信任运行元数据；D 使用 `runId` 关联推荐记录但不得访问 `agent_*` 表。只读调用的 `clientRequestId` 和 `idempotencyKey` MUST 为空。

#### Scenario: 从槽位构造并调用完整推荐 Command
- **WHEN** 当前可开始节点的必填和可选约束均来自已校验的 `SLOT` 引用
- **THEN** B 构造与槽位值一一对应的 `RankMoviePlanCommand`
- **AND** B 仅调用一次 `RankMoviePlanTool.execute(context, command)`
- **AND** B 不调用 A/D 的 Controller、Mapper、Repository、持久化代码或本应用 HTTP 接口

### Requirement: 推荐结果必须完全按现有状态机规则推进

系统 SHALL 把 D 返回的 `ToolResult<RecommendationPlanResult>` 和参数转换失败结果交给 `ExecutionPlanStateMachine.recordToolResult()`，不得由 D 或适配器直接改写节点状态、计划、重规划次数、SSE、会话或持久化状态。空方案和放宽建议是成功结果；卡片发送由 B 在运行状态成功后按 `recommendation-presentation-contract` 执行。

#### Scenario: 无可购方案是成功结果
- **WHEN** D 返回 `status=SUCCESS`、空方案和可空放宽建议
- **THEN** 状态机将该节点推进为 `SUCCESS`
- **AND** 系统不自动放宽约束或创建订单
