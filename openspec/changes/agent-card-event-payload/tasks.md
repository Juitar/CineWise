# 任务

- [x] B：补齐 SSE 事件 `planId`，验证 Agent Controller 单元测试。
- [x] B：实现六类卡片 payload 白名单校验器及正常/未知/缺字段测试。
- [x] B：新增 C 的卡片、确认结果、事件序列 JSON 夹具。
- [x] B：完成 proposal、spec、design，写明 QUESTION 与确认接口。
- [x] C：确认 `fixtures/agent/c/` 可用于卡片只读投影和位置授权交互；确认结果仅作只读展示，确认写请求移至独立 change `frontend-agent-action-confirmation`。（已在 PR #98 确认）
- [ ] B、C：联调真实 SSE 的旧计划、跳号和 `stream.reset` 恢复。（需 C 联调）
- [x] B：运行 `openspec validate agent-card-event-payload --strict`、相关单元测试、`backend/mvnw.cmd verify`、`git diff --check`。
