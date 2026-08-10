## 1. 意图和本轮工具范围

- [x] 1.1 B：新增受控 `AgentIntent`、类型化意图识别请求/响应和保守降级；验证：非法 JSON、未知意图、超时和异常均为 `GENERAL_CHAT`。
- [x] 1.2 B：实现按意图及可信出行任务上下文计算的 Tool 可见范围，并接入初始规划和重规划；验证：观影仅见三个观影 Tool，普通对话为空，出行无可信上下文不可见。

## 2. 安全文本和追问

- [x] 2.1 B：补齐 `TEXT` 回复类型、受控 payload、Mock/DeepSeek 回复生成和既有消息/SSE 映射；验证：普通对话产生可显示文本且不泄露内部字段。
- [x] 2.2 B：将缺失字段追问改为仅检查本轮允许且已校验计划使用的 Tool，并拦截内部及业务引用 ID；验证：观影正式槽位仍可追问，`travelTaskId` 等字段只能安全文本兜底。

## 3. 定向测试

- [x] 3.1 B：覆盖“这啥”/问候、普通文本模型失败和无可信出行上下文；验证：不生成计划、不执行 Tool、只返回安全 `TEXT`。
- [x] 3.2 B：覆盖明确观影请求、正常观影追问及有可信出行任务上下文的 Tool 注入；验证：允许列表、计划校验、用户归属调用边界正确。
- [x] 3.3 B：回归消息持久化、SSE、会话历史和运行查询；验证：不新增迁移、额外 run 或 SSE 事件种类。

## 4. 验证和交付检查

- [x] 4.1 B：执行相关 Agent 单元测试、编译、`openspec validate agent-intent-routing-safe-chat-fallback --strict` 和 `git diff --check`。
- [x] 4.2 B：功能完成后执行一次 `backend/mvnw.cmd verify`；记录本地结果，并将 MySQL 8.4 留给 GitHub Actions CI 验证。
