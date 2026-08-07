## Why

线上 `/assistant` 创建 Agent 会话时，后端全局 JSON 配置会省略 `null`，导致响应缺少 `summary`。前端把缺失字段视为无效 DTO，用户无法进入 Agent 工作区，模型调用尚未开始即失败。

## What Changes

- `AgentSessionResponse` 强制输出 `summary`，空摘要输出 JSON `null`。
- 为创建会话接口补充响应 JSON 回归断言。
- 不新增字段、不改变字段含义、不修改 SSE、模型、会话状态或前端解析规则。

## Capabilities

### New Capabilities

- `agent-session-nullable-summary`: Agent 会话响应在摘要为空时仍返回显式 `summary: null`。

### Modified Capabilities

- 无。

## Impact

- `backend/src/main/java/com/miaoyu/ticket/agent/api/AgentSessionResponse.java`
- `backend/src/test/java/com/miaoyu/ticket/agent/AgentControllerTest.java`
- C 的 Agent 会话创建与页面启动；不影响 A、D 模块或数据库。
