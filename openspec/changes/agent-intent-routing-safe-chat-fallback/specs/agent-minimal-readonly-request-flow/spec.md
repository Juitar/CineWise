## MODIFIED Requirements

### Requirement: 最小主控必须从当前请求生成并重新校验候选计划
系统 SHALL 提供 B 的最小只读 Agent 应用入口，接收非空 `clientRequestId`、当前文本、服务端 `PlanValidationContext`、可信 `runId`、`traceId` 和正数剩余时限。系统 MUST 先取得服务端受控意图和本轮允许 Tool 集合；仅在意图允许规划时调用 `ModelGateway.generatePlan`，随后 MUST 使用当前服务端上下文和同一允许集合再次执行 `PlanSchemaValidator`。调用方不得传入候选计划、允许工具名称或节点 ID。

#### Scenario: 明确观影请求只看到观影工具
- **WHEN** 当前请求识别为 `MOVIE`
- **THEN** 模型只可见已登记的 `rankMoviePlan`、`queryAvailableDates`、`queryShows`，且不得看见 `getTravelAdvice`

#### Scenario: 普通对话跳过计划
- **WHEN** 当前请求识别为 `GENERAL_CHAT`
- **THEN** 系统不调用 `generatePlan`、不初始化 Tool 节点，并生成安全 `TEXT`
