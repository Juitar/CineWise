## Why

C 的 Agent 工作区目前只能识别 QUESTION、PLAN_CARD 等已有卡片，不能按统一 SSE 外层字段渲染 D 已提供的出行建议摘要。需要由 B 把 D 的只读结构化结果转换为受控的 `TRAVEL_ADVICE_CARD`，同时保证任务归属和位置、路线、原始 JSON 等隐私边界不被突破。

## What Changes

- 新增 `TRAVEL_ADVICE_CARD` 的 Agent 卡片 payload、后端响应 DTO、事件校验、持久化事件读取和 SSE 映射。
- 将已合入的 D `GetTravelAdviceTool` 结构化结果接入 B 的工具白名单、执行适配器和 Supervisor 回包，且只从当前会话中受控的 `travelTaskId` 槽位读取任务号。
- 增加 C 可直接消费的 JSON 夹具，以及正常、无快照、降级、过期、空建议、非法事件和敏感字段不外泄的测试。
- 不新增 `ROUTE_CARD`，不修改建单确认、退票确认、定位上传、距离推荐、模型调用或 D 的私有实现。

## Capabilities

### New Capabilities

- `agent-travel-advice-card`: Agent 将 D 的已校验出行建议摘要作为安全、可恢复的 `TRAVEL_ADVICE_CARD` SSE 卡片提供给 C。

### Modified Capabilities

- `agent-pluggable-tool-execution`: 将已合入的 `getTravelAdvice` 只读工具加入 B 的生产执行与结果回包规则。

## Impact

- 影响 `backend` 的 Agent 卡片 DTO/校验、运行事件映射、工具执行与测试夹具，以及 C 消费的 Agent 类型、契约、投影和夹具。
- 依赖 D 已公开的 `GetTravelAdviceTool.execute(ToolContext, GetTravelAdviceCommand)` 与 `TravelAdviceToolResult`；B 不调用 D 的 Controller、Repository、Entity 或 JSON 字段。
- 不新增数据库迁移、写操作、确认动作、外部 Provider 调用或前端网络请求。
