# 任务

- [x] C：核对最新 B 类型、固定夹具、Controller/SSE、前端解析和 PR #156，确认七类正式 payload 与统一建单确认映射；验证：差异表不包含猜测字段。
- [x] C：记录负责人决定，明确出行建议、路线和退票确认不阻塞当前主流程，后续由 B 分别开 change；验证：当前 change 不新增对应前端类型或代码任务。
- [x] C：补浏览器层建单确认结果未知恢复测试；验证：确认 POST 只发送一次，并按历史消息顶层 `runId` 查询运行快照。
- [x] C：运行 Agent 定向测试和相关桌面/移动 Playwright；验证：Agent 定向单测 124/124、工作区 Playwright 10/10 通过。
- [x] C：运行 `pnpm check`、OpenSpec 严格校验、后端 `verify`、`git diff --check` 和状态检查；验证：仅修改当前 OpenSpec、`PLAN_CARD` 校验与安全投影、主流程恢复测试。
- [x] C：按 PR #160 正式 `PLAN_CARD` DTO 收紧运行时校验并扩展安全投影；验证：合法固定夹具、缺字段、未知字段和允许展示字段测试。
- [x] C：复跑 Agent 定向测试、工作区 Playwright、`pnpm check`、后端 `verify`、OpenSpec 严格校验和 Git 检查；验证：前端 553/553、后端 860 项零失败，基于最新 `origin/dev` 全部通过。
- [x] C：合并 PR #172 后的最新 `origin/dev`，保留交互卡片并解决 `PLAN_CARD` 校验、投影、固定夹具和恢复测试冲突；验证：无未解决冲突且相关测试重新通过。
