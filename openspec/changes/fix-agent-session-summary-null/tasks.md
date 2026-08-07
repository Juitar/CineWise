## 1. 响应修复

- [x] 1.1 B：让 `AgentSessionResponse` 在摘要为空时显式序列化 `summary: null`；验证：Controller JSON 回归测试。

## 2. 验证

- [x] 2.1 B：运行 `AgentControllerTest`，确认创建会话和会话列表均保留空 `summary`；验证：10 个测试通过。
- [x] 2.2 B：运行 OpenSpec 严格校验、`git diff --check` 和提交前范围检查；验证：通过。
- [x] 2.3 B：准备提交前运行一次 `backend/mvnw.cmd verify`；验证：PR #184 的 GitHub Actions Backend Verify 完整任务通过，run 31207197422。
