## 1. 测试替身修复

- [x] 1.1 检查 `AgentPersistenceMySqlIntegrationTest` 中所有 `AgentToolExecutor` mock 的固定行为。
- [x] 1.2 为推荐执行器 mock 在 Bean 创建和 reset 后恢复 `targetName()` 与 `definition()`。

## 2. 验证

- [ ] 2.1 运行 `AgentPersistenceMySqlIntegrationTest` 的 MySQL 集成测试；环境不可用时记录替代验证与未验证原因。
- [x] 2.2 执行严格 OpenSpec 校验和差异检查。

> 本地未设置 `CINEWISE_MYSQL_AGENT_PERSISTENCE_IT=true`，定向 MySQL 测试的 12 个用例均按条件跳过；已通过编译及 `AgentToolExecutorRegistryTest`、`AgentToolConfigurationTest`。合并前必须由 GitHub Actions MySQL 8.4 CI 完成 2.1。
