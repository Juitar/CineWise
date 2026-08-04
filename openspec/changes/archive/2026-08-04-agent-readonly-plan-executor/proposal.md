## 背景与目标

前面的 Change 已完成计划校验、运行状态机和 `rankMoviePlan` 类型化调用，但当前调用方仍要自己准备候选计划、选择节点并解释工具结果，B 还没有一条从当前请求到结构化回复的最小主控流程。本 Change 将这些基础能力扩展成可直接测试的只读 Agent 请求流程，证明主控可以生成受限计划、执行真实推荐并给出安全回复。

## 修改内容

- 新增 B 的最小只读 Agent 应用入口，接收 `clientRequestId`、当前文本、服务端确认槽位和可信 `runId`、`traceId`、剩余时限。
- 扩展 `PlanGenerationRequest`，让 `ModelGateway` 接收当前确认槽位；更新 `MockModelGateway`，在槽位完整时生成 `rankMoviePlan → RENDER_RESULT` 候选计划，缺少下一项必填槽位时生成 `ASK_USER` 候选计划。
- 主控不信任模型返回的可执行性结论，统一使用服务端 `ToolRegistry` 和 `PlanSchemaValidator` 重新校验候选计划；校验失败时不初始化运行、不调用 D。
- 校验成功后自动初始化和推进 `ExecutionRunState`，类型化调用既有 `RankMoviePlanExecutionAdapter`，并处理本轮 `ASK_USER`、`RENDER_RESULT`、失败和处理中结果。
- 新增 B 自有的结构化回复模型和回复事实映射，只把已校验、可展示的推荐字段传给 `ModelGateway.generateReply`；不把工具原始响应、模型原文或任意 `Map<String,Object>` 作为回复协议。
- 增加从当前请求到正常推荐、无场次降级、缺槽位追问、非法计划、参数错误、重试后失败和处理中回复的测试。

## 能力

### New Capabilities

- `agent-minimal-readonly-request-flow`：定义 B 如何从当前请求和服务端确认槽位开始，生成并校验计划、执行 `rankMoviePlan`，最后返回结构化只读回复和运行快照。

### Modified Capabilities

- `agent-plan-validation`：扩展 Mock 计划请求和确定性候选计划行为，使完整槽位生成只读推荐计划，缺失槽位生成最小追问计划，并继续接受服务端校验。

## 影响范围

- 影响 `backend/src/main/java/com/miaoyu/ticket/agent/application/**`、`agent/infrastructure/model/MockModelGateway.java` 和对应 Agent 测试。
- 复用已合入的 `PlanSchemaValidator`、`ExecutionPlanStateMachine`、`RankMoviePlanExecutionAdapter`、`ToolRegistry` 和 D 的 `RankMoviePlanTool`；不修改 A/D 的业务规则或公共 DTO。
- 不新增 Controller、认证、会话、MySQL、Redis、SSE、前端、写工具、确认动作、真实模型 SDK 或其他领域工具。
