## Why

最小只读工具在异步 POST SSE 任务中抛异常时，安全失败事实已经持久化，但当前请求没有重放失败事件，浏览器只能等待超时。需要让同一条连接立即收到已保存的安全错误和终态事件。

## What Changes

- 在 Agent POST SSE 异步任务内捕获最小只读工具异常，并只重放本次会话已持久化的失败事件。
- 保持异常原文不进入 SSE；发送后正常结束连接并停止心跳。
- 新增 Controller 回归测试，覆盖工具异常后的安全失败事件与正常结束。
- 不修改公开路径、请求参数、错误码、表结构、V008/V009、确认动作或 A/D 工具接口。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `agent-post-sse-interaction`: 工具异常后，当前 POST SSE 连接也必须重放已持久化的安全失败事件。

## Impact

- 后端：`agent/api/AgentController`、`AgentInteractionRuntimeService` 的只读重放入口及 Agent Controller 测试。
- 调用方：C 不修改接口，只会在已有连接中收到 `message.error` 和 `run.complete`。
- 不新增跨模块依赖、数据库迁移或外部服务调用。
