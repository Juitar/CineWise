## 背景

当前 B 已有 `ModelGateway`/`MockModelGateway`、`PlanSchemaValidator`、`ExecutionPlanStateMachine` 和 `RankMoviePlanExecutionAdapter`，但它们没有共同的应用入口。现有 Mock 只生成 `RENDER_RESULT`，回复也只是输入哈希；真实推荐适配器要求调用方先给出运行状态和 `nodeId`。这些组件分别可测，却不能证明 Agent 能从一次当前请求走到推荐回复。

本 Change 在 `agent.application` 增加一个内存、同步、只读的最小主控用例。调用方仍负责从可信服务端来源提供确认槽位和运行元数据；本 Change 不提前实现 Controller、认证、会话或持久化。

## 目标与非目标

### 目标

- 从当前文本、确认槽位和可信运行元数据开始，调用 Mock 模型生成受限候选计划。
- 对任何候选计划再次执行服务端白名单和 Schema 校验，校验成功后才创建运行快照。
- 自动处理本轮 `rankMoviePlan`、`ASK_USER` 和 `RENDER_RESULT` 节点，调用方不再传 `nodeId`。
- 把已校验工具结果映射为 B 自有的安全回复事实，并生成 `QUESTION`、`PLAN_CARD`、`MOVIE_CARD`、`PROGRESS` 或 `ERROR` 结构化回复。
- 返回候选计划校验结果、最终运行快照、实际工具结果和结构化回复，供后续 Controller/SSE/持久化 Change 复用。

### 非目标

- 不从自然语言中自行提取或确认槽位；调用方只可传服务端已确认槽位。
- 不实现真实模型供应商、Prompt、Controller、认证、会话、多轮上下文、MySQL、Redis、SSE、轨迹、并行线程池或进程崩溃恢复。
- 不实现写工具、确认动作、支付、`queryShows`、`querySeats`、天气、路线或其他工具。
- 不定义 C 的最终 HTTP/SSE 卡片 DTO，也不修改 A/D 的接口、业务规则和数据事实。

## 设计决定

### 1. 一个应用入口负责完整的最小只读请求

新增 `MinimalReadOnlyAgentService`（最终名称按现有包命名调整）及请求/结果类型。请求包含：

- `clientRequestId` 和当前文本；
- `PlanValidationContext`，其中槽位值来自服务端确认后的 `SlotSnapshot`；
- 可信 `runId`、`traceId` 和正数 `remainingDeadlineMs`。

结果包含：

- 本次 `PlanValidationResult`；
- 校验成功时的最终 `ExecutionRunState`；
- 按实际调用顺序排列的 `ToolResult<FixedRecommendationResult>`；
- 一条 B 内部结构化回复。

调用方不再准备 `CandidatePlan` 或传递 `nodeId`。保留这些底层类型是为了单元测试和后续恢复流程，不继续向上暴露拼装责任。

### 2. 模型只提候选计划，服务端重新决定能否执行

`PlanGenerationRequest` 增加不可变的确认槽位快照。最小主控从 `ToolRegistry` 读取允许的只读工具名称并构造请求，不能由用户文本指定允许工具。

`MockModelGateway` 根据 `clientRequestId + 当前文本 + 确认槽位 + 允许工具列表` 生成可复现计划：

- `movieId`、`cinemaId`、`date` 均存在时，生成 `CALL_TOOL(rankMoviePlan)`，随后生成依赖它的 `RENDER_RESULT`；只有 `timeFrom`、`timeTo` 同时存在时才引用时间范围。
- 缺少必填槽位时，只生成一个 `ASK_USER`，询问白名单定义顺序中的第一项缺失字段，不调用工具。
- `rankMoviePlan` 不在服务端允许列表时，不生成该工具节点。

Mock 通过注入同一个 `ToolRegistry` 读取 `rankMoviePlan` 的必填字段和声明顺序，不在模型适配器中复制一份字段常量。Spring 装配显式提供 `PlanSchemaValidator`、开发/测试用 `ModelGateway` 和最小主控服务 Bean，保证该流程不只存在于测试代码中。

无论候选来自 Mock 还是真实模型，主控都使用当前请求携带的 `PlanValidationContext` 再次调用 `PlanSchemaValidator`。不复用或信任模型层自己附带的校验结论；失败时返回结构化错误回复，不初始化状态机、不调用 D。

### 3. 主控按状态机选择结果处理三类节点

主控每次只处理 `ExecutionPlanStateMachine.selectRunnableNodes()` 返回的节点，并按计划顺序执行：

- `CALL_TOOL + rankMoviePlan`：委托既有 `RankMoviePlanExecutionAdapter`，它负责槽位转换、`ToolContext` 和状态机反馈。
- `ASK_USER`：根据第一项缺失的必填槽位生成 `QUESTION`，通过 `startNode()`、`succeedNode()` 记录该问题已经产出；本 Change 不等待下一轮输入。
- `RENDER_RESULT`：只读取已校验推荐结果，生成回复后通过 `startNode()`、`succeedNode()` 完成节点。

`COMPUTE`、`VALIDATE`、`CONFIRM_ACTION`、未知工具和写工具不在本 Change 执行范围。若出现这些节点，主控停止本轮并返回安全错误，不自行补充行为。

只读节点最多自动重试一次，因此循环上限为 `计划节点数 × 2`，而不是“计划节点数”。最大 12 节点时最多 24 次节点处理，既允许一次重试，也避免异常计划导致无限循环。`PROCESSING` 结果保留节点 `RUNNING`，立即返回 `PROGRESS`，同一轮不重复调用。

### 4. 回复只接收可展示的已校验事实

扩展 B 的回复请求/响应为类型化结构。主控把 `FixedRecommendationResult` 映射为 B 自有的推荐回复事实，只保留候选业务引用、可购标记、价格、开场时间、来源、时效、缺失因素和降级标记；不传工具异常、完整上下文或原始第三方响应。

回复类型按以下规则确定：

- 缺必填槽位：`QUESTION`；
- 存在可购场次：`PLAN_CARD`；
- 成功但 `purchaseEligible=false`：`MOVIE_CARD`，保留 `missingFactors` 和降级信息，不伪造场次；
- `PROCESSING`：`PROGRESS`；
- 计划非法或工具最终失败：`ERROR`，只包含稳定问题/错误码和安全文案。

`MockModelGateway.generateReply` 对相同请求标识、当前文本、输出类型和相同回复事实返回相同结构化回复。确认槽位不重复进入回复请求。该内部回复不是 C 的最终 HTTP/SSE DTO；后续交互 Change 负责映射公共卡片协议。

### 5. 失败、重复调用和时间边界

- 计划校验失败：无运行快照、无工具结果、D 调用次数为零。
- 槽位结构类型正确但值无法构造 `RankMoviePlanCommand`：沿用 `FAILED + 100001 + retryable=false`，由状态机跳过依赖的渲染节点，主控返回安全 `ERROR`。
- D 返回可重试失败：仅按状态机的 `RETRY_ONCE` 再调用一次；最终失败返回 `ERROR`，独立分支仍可继续。
- D 返回无场次成功降级：节点为 `SUCCESS`，回复保留 `purchaseEligible=false`、`missingFactors=[SHOWTIME]` 和 `degraded=true`。
- 本 Change 无持久化幂等。相同只读请求会得到相同 Mock 计划和回复，但再次调用服务仍可重新执行只读工具；不得把这一行为描述成会话请求去重。
- 工具适配继续使用 `min(remainingDeadlineMs, ToolDefinition.timeout)`；本 Change 不增加线程中断或跨进程总时限恢复。

## 风险与处理

- [仍由调用方提供确认槽位] → 明确入口只接受服务端上下文；自然语言槽位提取在后续会话 Change 实现，不能把模型猜测标记为确认。
- [Mock 不是实际大模型] → 先用确定性计划和回复覆盖主控流程；真实供应商接入时保持同一 `ModelGateway` 边界并重新经过服务端校验。
- [内部回复不是前端协议] → 使用 B 自有类型，避免在 C 未接入时提前承诺 HTTP/SSE 载荷；后续只做明确映射。
- [内存流程不能断线恢复] → 返回不可变快照和结果；MySQL、Redis、事件续传和写工具恢复由后续 Change 实现。

## 迁移和回退

1. 扩展模型计划/回复请求类型及 Mock 行为，同时更新既有测试调用方。
2. 新增最小主控请求、结果、回复事实和应用服务。
3. 接入既有只读运行协调及 D 推荐工具，增加从请求到回复的联合测试。
4. 执行受影响测试、`backend/mvnw.cmd verify` 和 OpenSpec 严格校验。
5. 不涉及数据库、配置、API 或部署迁移；回退时移除新应用服务并恢复 Mock 请求/回复类型，不影响 D 工具。

## 待确认事项

无。D 已确认 `RankMoviePlanTool` 及结果语义未变；本 Change 不新增 A/C/D 公共接口。
