## Context

`GetTravelAdviceTool` 已作为 D 的公开只读 Tool 合入：`execute(ToolContext, GetTravelAdviceCommand)` 返回 `ToolResult<TravelAdviceToolResult>`。B 已有白名单定义和 `GetTravelAdviceExecutionAdapter`，但 Agent 卡片协议、运行结果到回复的映射、SSE 校验和 C 的投影尚不认识出行建议。现有 `AgentCardEventValidator` 对未知卡片返回安全文本，非法 payload 不得推动前端续传游标。

本次由 B 负责 Agent 卡片、SSE 和 Tool 适配；D 负责 Tool 的结构化结果和本人任务校验；C 只按 `eventType + payload` 渲染。当前用户由 D 的 `CurrentUserAccessor` 处理，B 不接收或传递 `userId`。

## Goals / Non-Goals

**Goals:**

- 定义稳定的 `TRAVEL_ADVICE_CARD` 安全摘要，并让事件持久化恢复与实时 SSE 使用同一 payload。
- 仅允许 `getTravelAdvice` 从当前执行节点受控的 `travelTaskId` 槽位取值，调用 D 的公开 Tool，并将结构化结果映射为卡片。
- 覆盖无快照、降级、过期、空建议、未知/非法事件和敏感字段拦截，提供 C 可直接消费夹具。

**Non-Goals:**

- 不实现 `ROUTE_CARD`，不查询位置、不创建或刷新出行任务、不发送提醒。
- 不解析或透传 `weatherJson`、`adviceJson`，不访问 D 的 Controller、Repository、Entity 或 Mapper。
- 不修改建单/退票确认、路线卡、定位上传、距离推荐、模型调用及其他 Owner 私有代码。

## Decisions

### 1. 用 B 的专用回复事实和受控 DTO 隔离 D 结果

新增 Agent 侧的出行建议回复事实与 payload DTO，只复制 `TravelAdviceToolResult` 中的展示字段：任务号/状态、可用性、天气摘要、建议条目、降级和时效信息。不会把 D 的结果对象、内部 JSON 字符串或持久化类型放进 SSE/事件 JSON。这样卡片持久化和 C 的恢复不依赖 D 的类。

天气仅表达 `area`、`condition`、`risk`；建议条目仅表达 `type`、`text`。映射时禁止出现 `userId`、经纬度、精确起点、路线折线、途经点、地图几何、原始天气 JSON 或原始建议 JSON。

### 2. `available=false` 是成功的只读卡片

工具成功但没有已生成快照时仍产生 `TRAVEL_ADVICE_CARD`，携带任务号、状态和 D 返回的 `source`，`weather=null`、`advice=[]`、`dataAt=null`、`expiresAt=null`、`degraded=false`、`fallbackType=null`。它不触发重试、刷新、任务创建或确认；C 可直接显示“建议尚未生成”。过期卡片仍只读，保留原时效字段与 `expired=true`。

### 3. Slot 是唯一任务号来源

`getTravelAdvice` 的 ToolDefinition 保持 `readOnly=true`、不需要确认。执行适配器只接受名为 `travelTaskId` 且来源为 `SLOT` 的单一输入引用；缺失或伪造槽位会在调用 D 前安全失败。D Tool 继续用 `CurrentUserAccessor` 做本人校验；`ToolContext` 只携带运行元数据，不携带 `userId`。

### 4. 先校验再投影、统一实时与恢复格式

卡片事件统一保持既有 `eventId`、`runId`、`eventType=card` 和 `payload` 外层结构。`AgentCardEventValidator` 对完整的 `TRAVEL_ADVICE_CARD` 返回 `RENDER`；字段非法或出现不允许的字段时返回 `REJECT`，前端不能推进 SSE 游标。C 类型、contract、projection 和 JSON 夹具按同一字段表处理实时事件与历史恢复。

## Risks / Trade-offs

- [D 后续修改公开 Result] → 本次以当前已合入的精确签名为依据；若签名改变，先更新 D 的公开 Tool 与本 change 的设计/spec，再改 B Adapter，不做反射或兼容分支。
- [旧事件没有出行卡片] → 未知类型维持现有安全文本处理；不会为历史记录推断新字段。
- [D 返回建议为空或天气缺失] → 保留空数组/空天气与降级标志，绝不补造实时天气或路线文本。
- [对话运行异常] → 按现有安全错误事件处理，不把任务是否存在、是否本人或异常原文暴露给模型或 C。

## Migration Plan

1. 扩展 B 的卡片 DTO、校验、回复映射和 C 协议/夹具，不修改持久化表结构。
2. 使用已有 `GetTravelAdviceTool` 的公开结构化结果接入 Supervisor 回包；失败继续沿用安全错误映射。
3. 先跑定向单元测试，再执行一次 `backend/mvnw.cmd verify` 与严格 OpenSpec 校验。
4. 如需回退，停止生成新卡片即可；旧 SSE/持久化事件仍由现有未知卡片降级逻辑安全处理。

## Open Questions

无。D 的当前公开签名已合入并可由 B 直接调用：`GetTravelAdviceTool.execute(ToolContext, GetTravelAdviceCommand)`，结果为 `ToolResult<TravelAdviceToolResult>`。
