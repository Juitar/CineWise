## 1. 修复实现

- [x] 1.1 Owner B：已修正 `PROCESSING` 与下游 `PENDING` 的运行状态判定，补组合状态测试。
- [x] 1.2 Owner B：已将唯一键冲突后的胜者读取移到独立事务，补 MySQL 并发相同 `clientRequestId` 测试。
- [x] 1.3 Owner B：已按 Unicode 码点生成请求摘要并补 emoji、未配对代理字符测试。
- [x] 1.4 Owner B：已新增按 run ID 和用户读取消息，实现旧请求完整快照并补回归测试。

## 2. 验证与交付

- [x] 2.1 Owner B：定向 Agent 测试 19 项通过（4 项 MySQL 用例因本机安全开关跳过），`backend/mvnw.cmd verify`、OpenSpec 严格校验与 `git diff --check` 均通过。
- [ ] 2.2 Owner B：提交并推送 PR #44 修复，按用户授权回复审查意见并请求复审。
