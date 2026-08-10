## Why

普通对话可能被模型错误规划为 `getTravelAdvice`，随后服务端把仅应由可信上下文提供的 `travelTaskId` 变成向用户追问的字段。这既暴露内部实现概念，也会让普通观影对话无法正常回复。

## What Changes

- 在 Agent 调用计划模型前增加受控意图识别：`MOVIE`、`TRAVEL`、`GENERAL_CHAT`。
- 按意图和已校验的服务端上下文计算本轮可见 Tool，不再把全局注册表直接提供给模型。
- 为普通对话生成受控 `TEXT` 回复，并通过现有消息持久化和 SSE 格式输出。
- 收紧缺失字段追问：只能检查本轮允许且已校验计划实际使用的 Tool；内部或业务引用 ID 一律不能成为 `QUESTION`。
- 意图识别异常、非法结果或缺少可信出行任务上下文时，安全降级为普通 `TEXT`，不执行 Tool。

## Capabilities

### New Capabilities

- `agent-intent-routing-safe-chat`: 服务端识别本轮 Agent 意图，并以受控文本回复处理普通或不确定对话。

### Modified Capabilities

- `agent-minimal-readonly-request-flow`: 计划请求改为使用本轮服务端计算的允许 Tool 列表。
- `agent-replanning-supervisor`: 初始规划、重规划和追问只依据本轮允许的已校验计划。
- `agent-real-model-gateway`: DeepSeek 和 Mock 网关支持受控意图识别及 `TEXT` 回复。
- `agent-acceptance-sse-replies`: 现有消息和 SSE 回复支持安全的 `TEXT` 类型。
- `agent-pluggable-tool-execution`: `getTravelAdvice` 仅在可信、已校验的出行任务上下文存在时可见。

## Impact

影响 B 的 Agent 意图路由、`MultiToolSupervisor`、模型网关、回复映射、SSE 和定向测试。不会修改 D 的出行 Tool、订单业务、数据库表、Flyway、前端协议或让前端提交 `travelTaskId`。
