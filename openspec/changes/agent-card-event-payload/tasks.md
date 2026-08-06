# 任务

- [x] B：补齐 SSE 事件 `planId`，验证 Agent Controller 单元测试。
- [x] B：实现六类卡片 payload 白名单校验器及正常/未知/缺字段测试。
- [x] B：新增 C 的卡片、确认结果、事件序列 JSON 夹具。
- [x] B：完成 proposal、spec、design，写明 QUESTION 与确认接口。
- [ ] C：用 `fixtures/agent/c/` 接入卡片 reducer、位置授权 UI 和确认请求。（需 C 联调）
- [ ] B、C：联调真实 SSE 的旧计划、跳号和 `stream.reset` 恢复。（需 C 联调）
- [x] B：运行 `openspec validate agent-card-event-payload --strict`、相关单元测试、`backend/mvnw.cmd verify`、`git diff --check`。
