## 为什么要做

B 已有工具协议、计划校验和纯 Java 运行状态机，D 已提供可直接调用的
`RankMoviePlanTool.execute(ToolContext, RankMoviePlanCommand)`。两者之间还没有正式白名单登记和类型化执行适配，已校验的 `rankMoviePlan` 节点不能安全地变成 D 的 Command，也不能把结果交回既有状态机。

## 本次做什么

- 在 B 的 Agent 工具白名单登记只读工具 `rankMoviePlan`：Command 为 `RankMoviePlanCommand`，结果为 `FixedRecommendationResult`，字段和 Java 类型与 D 当前实现完全一致。
- 新增 B 的窄适配器：只从已校验运行计划的节点和槽位快照构造 Command，并且只直接调用 `RankMoviePlanTool.execute(context, command)`。
- 用 B 受信任的运行元数据构造 `ToolContext`；槽位值不放入 `ToolContext`，只用于构造 Command。
- 将 D 返回的 `ToolResult<FixedRecommendationResult>` 原样交给 `ExecutionPlanStateMachine`，覆盖成功、无场次成功降级、参数不合法和失败后的状态处理。
- 增加 B-D 最小集成测试，验证真实 D 工具的正常和无场次结果，以及 B 对参数错误和失败 `ToolResult` 的处理。

## 本次不做什么

- 不新增 Controller、会话、MySQL、Redis、SSE、认证、前端、确认动作、`actionId`、写工具或支付。
- 不接入 `queryShows`、`querySeats` 或其他工具，不新增通用反射路由、Bean 名路由或字符串方法调用。
- 不改 A 的场次查询规则，也不改 D 的推荐、候选、降级或数据时效规则。
- 不生成计划、不重规划、不发布 SSE、不修改运行持久化；本次只消费已校验计划，并复用现有内存状态机。

## 影响范围和负责人

- B：`agent` 的白名单定义、槽位到 Command 的转换、调用适配、状态机衔接和测试。
- D：只作为既有公开 `RankMoviePlanTool`、`RankMoviePlanCommand`、`FixedRecommendationResult` 的提供方；本次不修改 D 代码。
- A、C：无代码和接口改动。

## 已确认依据

- `openspec/changes/d-demo-content-recommendation-baseline/tasks.md` 第 1.3、4.4、4.5 已记录 B 对工具名称、Command 和公共结果的确认，且当前实现与记录一致。
- `RankMoviePlanTool` 只调用 D 的 Application Service；D 已通过公开 `ShowQueryService` 取得场次事实。B 不访问 A/D 的 Controller、Mapper、Repository 或持久化代码。

## 验收结果

- 合法的已校验节点可用五个已声明槽位调用 D 的唯一公开工具方法，状态机收到同一个类型化结果。
- 无可购场次仍以 `SUCCESS + purchaseEligible=false + missingFactors=[SHOWTIME]` 结束节点，不被当作失败或重试。
- 槽位缺失、格式错误、时段不成对或开始时间不早于结束时间时，不调用 D；B 以 `100001` 的不可重试失败交给状态机。
- 测试证明没有反射、Bean 名或任意字符串执行入口，也没有计划、SSE 或运行持久化副作用。
