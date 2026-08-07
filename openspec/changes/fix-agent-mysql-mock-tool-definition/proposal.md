## Why

`AgentPersistenceMySqlIntegrationTest` 在 GitHub Actions 的 MySQL 8.4 环境完成数据库初始化后，因测试替身缺少工具定义而无法创建 Spring ApplicationContext。该问题阻断 `dev` 的 MySQL 集成测试，需以最小测试修复恢复验证。

## What Changes

- 为 `RankMoviePlanExecutionAdapter` 的 MySQL 测试 mock 同时配置 `targetName()` 和 `definition()`。
- 在每次 `Mockito.reset(...)` 后恢复上述两项固定行为。
- 将监督器测试计划夹具更新为当前完整 `rankMoviePlan` 的必填输入和结果类型。
- 将仍依赖旧固定推荐服务的 B Agent 测试替换为完整推荐结果替身。
- 检查同一测试配置中的其他 `AgentToolExecutor` mock，防止出现同类空工具定义。

## Capabilities

### New Capabilities

- `agent-mysql-test-context`: Agent MySQL 集成测试的工具执行器 mock 能在 Spring 上下文初始化期间提供完整工具定义。

### Modified Capabilities

- 无。

## Impact

- 仅影响 `AgentPersistenceMySqlIntegrationTest` 及其 OpenSpec 验收记录。
- 不修改生产代码、MySQL 配置、Flyway、接口或业务规则。
