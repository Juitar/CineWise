## Purpose

定义 B 如何从当前请求和服务端确认槽位开始，生成并校验计划、执行 `rankMoviePlan`，最后返回结构化只读回复和运行快照。

## Requirements

### Requirement: 最小主控必须从当前请求生成并重新校验候选计划

系统 SHALL 提供 B 的最小只读 Agent 应用入口，接收非空 `clientRequestId`、当前文本、服务端 `PlanValidationContext`、可信 `runId`、`traceId` 和正数剩余时限。系统 MUST 从 `ToolRegistry` 取得允许的只读工具名称并调用 `ModelGateway.generatePlan`，随后 MUST 使用当前服务端上下文再次执行 `PlanSchemaValidator`；调用方不得传入候选计划、允许工具名称或节点 ID。

#### Scenario: 完整请求生成合法推荐计划

- **WHEN** 当前请求具有服务端确认的 `movieId`、`cinemaId` 和 `date`，且 `rankMoviePlan` 在只读白名单中
- **THEN** 模型网关返回包含 `rankMoviePlan` 和下游 `RENDER_RESULT` 的候选计划
- **AND** 主控使用服务端上下文重新校验候选计划后才初始化运行状态

#### Scenario: 拒绝模型返回的非法计划

- **WHEN** 模型网关返回未知工具、非法依赖、错误输入类型或其他不合法候选计划
- **THEN** 主控返回完整 `PlanValidationResult` 和安全 `ERROR` 回复
- **AND** 不创建运行快照、不调用 D 工具

### Requirement: 缺少必要槽位必须只追问下一项

当 `rankMoviePlan` 缺少必填槽位时，系统 SHALL 按工具白名单输入定义顺序只选择第一项缺失字段，生成单个 `ASK_USER` 节点和 `QUESTION` 回复。系统 MUST NOT 因可选 `timeFrom`、`timeTo` 缺失而追问，也不得在必填槽位不完整时调用 D。

#### Scenario: 缺少日期时只询问日期

- **WHEN** 服务端已确认 `movieId`、`cinemaId`，但没有确认 `date`
- **THEN** 候选计划只包含一个询问 `date` 的 `ASK_USER` 节点
- **AND** 最终回复类型为 `QUESTION`，D 工具调用次数为零

#### Scenario: 可选时间范围为空时直接推荐

- **WHEN** `movieId`、`cinemaId`、`date` 均已确认且 `timeFrom`、`timeTo` 均为空
- **THEN** 系统生成并执行不含时间范围引用的 `rankMoviePlan` 节点
- **AND** 不生成时间追问节点

### Requirement: 合法只读计划必须自动执行到结构化回复

候选计划校验成功后，系统 SHALL 初始化 `ExecutionRunState`，并按计划顺序处理状态机选出的 `rankMoviePlan`、`ASK_USER` 和 `RENDER_RESULT` 节点。`rankMoviePlan` MUST 只委托既有 `RankMoviePlanExecutionAdapter`；`ASK_USER` 和 `RENDER_RESULT` MUST 通过 `ExecutionPlanStateMachine.startNode()` 与 `succeedNode()` 推进。系统 MUST 返回最终运行快照、按调用顺序排列的 `ToolResult<FixedRecommendationResult>` 和一条结构化回复。

#### Scenario: 从请求自动执行到可购推荐回复

- **WHEN** 合法请求生成一个可执行推荐节点，且 D 返回至少一个可购场次
- **THEN** 主控自动执行推荐节点和下游渲染节点
- **AND** 返回 `PLAN_CARD` 结构化回复、原样工具结果和全部成功的节点状态

#### Scenario: 失败分支不阻断独立节点

- **WHEN** 一个推荐节点最终失败，而另一个当前可开始节点不依赖该失败节点
- **THEN** 状态机跳过失败节点的传递下游
- **AND** 主控继续处理独立节点，并在最终结果中保留实际执行顺序

### Requirement: 回复只能使用已校验的可展示事实

系统 SHALL 将成功的 `FixedRecommendationResult` 映射为 B 自有的类型化回复事实，只保留候选业务引用、可购标记、价格、开场时间、来源、时效、缺失因素和降级标记。系统 MUST NOT 将工具原始响应、异常、模型原始上下文、用户身份或任意 `Map<String,Object>` 交给回复模型。计划非法或工具最终失败时，回复 MUST 只包含稳定问题或错误码和安全文案。

#### Scenario: 无场次保留成功降级事实

- **WHEN** D 返回 `SUCCESS`、`purchaseEligible=false`、`missingFactors=[SHOWTIME]` 和 `degraded=true`
- **THEN** 对应工具节点保持 `SUCCESS`，回复保留缺失因素和降级标记
- **AND** 回复不得伪造 `showId`、价格、开场时间或可购 `PLAN_CARD`

#### Scenario: 参数错误返回安全失败回复

- **WHEN** 已通过结构校验的槽位值无法构造合法 `RankMoviePlanCommand`
- **THEN** 主控保留既有 `FAILED + 100001 + retryable=false` 工具结果和状态机失败状态
- **AND** `ERROR` 回复只包含稳定错误码与安全文案，D 不被调用

### Requirement: 单轮执行必须支持一次重试并有明确上限

系统 SHALL 仅按 `ExecutionPlanStateMachine` 的现有规则重试只读工具，单个节点最多重试一次。单次主控调用处理节点的总次数 MUST NOT 超过计划节点数的两倍。收到 `PROCESSING` 后系统 MUST 保持节点为 `RUNNING`、返回 `PROGRESS` 并结束本轮，不得再次执行同一节点。范围外非工具节点、未知工具、写工具和确认节点 MUST 使本轮安全停止，不得被执行。

#### Scenario: 可重试失败后第二次成功

- **WHEN** `RETRY_ONCE` 推荐节点第一次返回 `FAILED + retryable=true`，第二次返回 `SUCCESS`
- **THEN** 主控在同一轮只重试一次并继续渲染成功结果
- **AND** 节点尝试次数为二，不发生第三次工具调用

#### Scenario: 处理中结果结束本轮

- **WHEN** 推荐工具返回 `PROCESSING`
- **THEN** 主控返回该工具结果、`RUNNING` 节点状态和 `PROGRESS` 回复
- **AND** 同一轮不再次调用该节点或执行依赖它的渲染节点

### Requirement: 最小请求流程必须保持确定性但不伪造持久化幂等

开发和测试环境中，相同 `clientRequestId`、当前文本、确认槽位和允许工具列表 SHALL 生成相同候选计划；相同请求标识、当前文本、输出类型和回复事实 SHALL 生成相同结构化回复。确认槽位 MUST NOT 重复进入回复请求。由于本 Change 不持久化会话或运行，系统不保证阻止相同请求再次执行只读工具；系统 MUST NOT 声称已经提供请求去重、断线恢复或写操作幂等。

#### Scenario: 重复生成相同计划和回复

- **WHEN** 使用完全相同的请求和固定 D 结果重复执行 Mock 流程
- **THEN** 候选计划标识、节点顺序、回复类型、文本和载荷保持一致
- **AND** 测试不把只读工具的再次调用误判为持久化恢复能力
