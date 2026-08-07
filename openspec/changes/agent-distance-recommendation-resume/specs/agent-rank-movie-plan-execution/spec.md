## MODIFIED Requirements

### Requirement: 已校验节点必须通过唯一类型化入口调用 D

系统 SHALL 只为已校验、当前可开始的只读 `CALL_TOOL` 节点构造 `RankMoviePlanCommand`，并且 MUST 只调用 `RankMoviePlanTool.executeRecommendationPlan(ToolContext, RankMoviePlanCommand)`。工具上下文的运行标识和追踪标识 MUST 来自 B 的受信任运行元数据；`runId` 不得进入 Command。普通推荐的 ToolContext 距离字段 MUST 均为 null。仅当同一 run 的受控位置上传成功时，B 才可在当前调用中提供 UUID `distanceContextId` 和 `distancePreference="NEAREST"`；两个字段不得来自模型、槽位或普通用户输入，也不得持久化。只读调用的 `clientRequestId` 和 `idempotencyKey` MUST 为空。

#### Scenario: 从槽位构造并调用完整推荐 Command
- **WHEN** 当前可开始节点的必填和可选约束均来自已校验的 `SLOT` 引用
- **THEN** B 构造与槽位值一一对应的 `RankMoviePlanCommand`
- **AND** B 仅调用一次 `RankMoviePlanTool.executeRecommendationPlan(context, command)`
- **AND** B 不调用 A/D 的 Controller、Mapper、Repository、持久化代码或本应用 HTTP 接口

#### Scenario: 距离恢复不污染 Command
- **WHEN** 已上传位置的同一 run 恢复原 rankMoviePlan
- **THEN** B 只在可信 ToolContext 中传递 UUID 与 NEAREST，并保持 Command、槽位、模型输入及持久化数据不含距离上下文或位置资料
- **AND** D 返回的 ToolResult 仍原样交给状态机
