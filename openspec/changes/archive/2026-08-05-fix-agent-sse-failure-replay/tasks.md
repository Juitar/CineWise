## 1. 失败事件重放

- [x] 1.1 B：在安全失败事务完成后抛出内部 `AgentFailurePersistedException`，并为运行时应用服务增加按原会话和游标只读重放已持久化事件的入口；验证：`AgentInteractionRuntimeServiceTest` 证明不创建运行、不调用工具、不构造异常载荷。
- [x] 1.2 B：在 `AgentController` 异步任务内只捕获 `AgentFailurePersistedException` 并重放失败事件，发送结束后关闭 emitter；验证：异常文本不进入 SSE，心跳按现有完成回调取消，其他运行时异常不重放历史事件。

## 2. 回归验证

- [x] 2.1 B：新增 Controller 测试，断言工具异常后当前连接收到 `message.error`、`run.complete` 并正常结束；验证：不出现异常原文，不重复提交或执行工具。
- [x] 2.2 B：执行 Agent 定向测试、`backend/mvnw.cmd verify`、`openspec validate fix-agent-sse-failure-replay --strict` 与 Git 检查；验证：定向 6 项测试通过；完整 verify 为 307 tests、0 failures、0 errors、18 skipped，Checkstyle/SpotBugs/JaCoCo 通过；无未验证项。
