# agent-message-history-run-id

## 背景与目标

确认 POST 超时、断网或结果未知时，C 不能重发确认 POST。C 需要先读取会话历史，从确认卡所属消息取得运行 UUID，再查询既有的 `GET /api/v1/agent/runs/{runId}` 恢复实际状态。

## 范围

- B 在 `GET /api/v1/agent/sessions/{sessionId}/messages` 的每条历史消息中输出所属运行的对外 `runId` UUID。
- B 保持现有当前用户和会话归属校验，并用批量查询避免一页消息逐条查运行。
- B 更新 C 的历史消息夹具和契约测试。

## 非范围

- 不新增 `GET /actions/{actionId}`，不修改确认 POST，也不自动重发确认。
- 不修改 C 的前端恢复实现，不修改 SSE、订单、画像、推荐或 Flyway。
- 不新增 Agent run 或额外 SSE。

## Owner 与验收

B 负责后端 DTO、查询映射、夹具与测试；C 是接口消费者，后续负责“重新拉历史消息 → 取确认卡消息的 runId → GET /runs/{runId}”的前端实现和测试。验收时历史消息顶层仅增加字符串 UUID `runId`，不新增内部运行主键、userId、actionId、订单号、幂等键或完整工具参数。既有确认卡 payload 可保留确认接口所需的 `actionId`，但不得扩充上述敏感字段。
