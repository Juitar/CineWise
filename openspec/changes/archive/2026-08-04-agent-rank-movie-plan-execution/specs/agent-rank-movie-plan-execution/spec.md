## Purpose

定义 B 如何将已校验的 `rankMoviePlan` 计划节点按类型调用 D 的推荐工具，并把统一结果交回既有运行状态机，而不扩大到计划生成、SSE、持久化或其他工具。

## ADDED Requirements

### Requirement: rankMoviePlan 必须作为只读类型化工具登记

系统 SHALL 在 B 的 Agent 工具白名单中以精确名称 `rankMoviePlan` 登记一个工具定义。该定义 MUST 使用 `RankMoviePlanCommand` 作为 Command 类型、`FixedRecommendationResult` 作为结果类型、`readOnly=true`、3 秒超时、`idempotencyRequired=false` 和允许错误码 `100001`。输入定义 MUST 严格为 `movieId:String`、`cinemaId:String`、`date:LocalDate` 三个必填字段，以及 `timeFrom:LocalTime`、`timeTo:LocalTime` 两个可选字段。

#### Scenario: 校验合法的 rankMoviePlan 节点

- **WHEN** 已注册白名单的候选计划节点引用 `rankMoviePlan`，并以匹配 Java 类型的槽位提供全部必填输入
- **THEN** `PlanSchemaValidator` 接受该只读工具节点
- **AND** 生成的运行节点保持服务端初始状态，不执行 D 工具

#### Scenario: 拒绝未知或类型不符的输入

- **WHEN** 节点缺少必填字段、引用未知输入字段，或将非 `String`、`LocalDate`、`LocalTime` 槽位用于对应输入
- **THEN** `PlanSchemaValidator` 按既有结构化问题拒绝计划
- **AND** 系统不得按字符串查找 Bean、类或方法

### Requirement: 已校验节点必须通过唯一类型化入口调用 D

系统 SHALL 只为已校验、当前可开始的只读 `CALL_TOOL` 节点构造 `RankMoviePlanCommand`，并且 MUST 只调用 `RankMoviePlanTool.execute(ToolContext, RankMoviePlanCommand)`。工具上下文的运行标识和追踪标识 MUST 来自 B 的受信任运行元数据；节点标识、目标名称、输入引用、时限和状态版本 MUST 来自已校验节点及白名单规则。只读调用的 `clientRequestId` 和 `idempotencyKey` MUST 为空。

#### Scenario: 从槽位构造并调用推荐 Command

- **WHEN** 当前可开始节点的三个必填输入及已声明的可选输入均来自已校验的 `SLOT` 引用，且槽位快照能解析为 D Command 所需类型
- **THEN** B 构造与槽位值一一对应的 `RankMoviePlanCommand`
- **AND** B 仅调用一次 `RankMoviePlanTool.execute(context, command)`
- **AND** B 不调用 A/D 的 Controller、Mapper、Repository、持久化代码或本应用 HTTP 接口

#### Scenario: 节点不满足调用前置条件

- **WHEN** 节点不是当前可开始的 `rankMoviePlan` 只读工具节点，或五个字段中任一字段来自 `NODE_RESULT`
- **THEN** B 拒绝调用
- **AND** D 的 `RankMoviePlanTool` 不被执行

### Requirement: 参数转换失败必须成为不可重试的统一失败结果

系统 SHALL 在从槽位解析或构造 `RankMoviePlanCommand` 失败时，不调用 D 工具，并创建 `status=FAILED`、`errorCode=100001`、`retryable=false`、`replanSuggested=false`、`suggestedNextAction=CHECK_INPUT` 的 `ToolResult<FixedRecommendationResult>`。系统 MUST 校验两个时间字段同时为空或同时存在，且存在时 `timeFrom` 早于 `timeTo`。

#### Scenario: 槽位不能构成合法 Command

- **WHEN** 必填槽位缺失、业务 ID 非法、日期或时间无法解析、只提供一个时间字段，或开始时间不早于结束时间
- **THEN** B 不调用 D 工具
- **AND** B 将不可重试的 `100001` 失败结果交给运行状态机

### Requirement: 推荐结果必须完全按现有状态机规则推进

系统 SHALL 把 D 返回的 `ToolResult<FixedRecommendationResult>` 和参数转换失败结果交给 `ExecutionPlanStateMachine.recordToolResult()`，不得由 D 或适配器直接改写节点状态、计划、重规划次数、SSE、会话或持久化状态。

#### Scenario: 无场次是成功降级

- **WHEN** D 返回 `status=SUCCESS`、`purchaseEligible=false`、`missingFactors=[SHOWTIME]` 且 `degraded=true`
- **THEN** 状态机将该节点推进为 `SUCCESS`
- **AND** 系统不重试该节点、不跳过其下游，也不生成 `PLAN_CARD`

#### Scenario: 工具失败按既有重试和跳过规则处理

- **WHEN** D 返回 `status=FAILED`，或 B 产生参数转换失败结果
- **THEN** 状态机仅按节点 `failurePolicy`、结果 `retryable` 和既有尝试次数决定是否重新排队
- **AND** 最终失败时状态机跳过未开始的传递下游节点

### Requirement: B-D 联合测试必须覆盖公开边界和结果语义

系统 SHALL 提供最小 B-D 联合测试，直接使用 D 的 `RankMoviePlanTool` 公开 Java 方法和 B 的已校验计划执行适配，不通过 Controller、反射、Bean 名或 HTTP 调用。

#### Scenario: 执行 B-D 最小集成测试

- **WHEN** 测试使用合法槽位执行正常推荐和无场次推荐，并分别提供参数错误和失败结果夹具
- **THEN** 测试验证 Command、ToolContext、结果类型和状态机状态符合本规格
- **AND** 测试验证 D 不参与计划生成、SSE 或运行状态修改
