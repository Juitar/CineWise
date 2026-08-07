# 任务

## 1. 用户工作区状态

- [x] 1.1 Owner：C；扩展 Agent 运行和工作区状态类型，接受 `WAITING_LOCATION` 且不改变字符串 ID、游标和计划版本规则；验证：合同与投影测试。
- [x] 1.2 Owner：C；在工作区把等待位置视为活动运行，禁用消息输入并保留取消入口，不自动重连或重发消息；验证：Hook 和工作区组件测试。

## 2. 验证

- [x] 2.1 Owner：C；在最新 `origin/dev` 上运行 Agent 定向单测和相关 Playwright；验证：记录通过、失败和跳过数，确认测试命令正常退出。
- [x] 2.2 Owner：C；运行 `pnpm check`、`openspec validate frontend-agent-runtime-contract-compatibility --strict`、`git diff --check` 和变更范围检查；验证：只修改 C 前端和当前 OpenSpec，不修改 B/A/D 后端与业务规则。

## 3. 历史消息兼容

- [x] 3.1 Owner：C；历史消息 DTO 接受 `payload=null`，投影和确认恢复使用空值安全读取，精简 `QUESTION` 只恢复为只读文本；验证：合同、投影和 Hook 定向测试。
- [x] 3.2 Owner：C；使用当前登录账号刷新真实历史会话，确认用户文本和问题文本可见、无格式错误且可以继续输入；验证：`http://127.0.0.1:8002` 真实联调。
