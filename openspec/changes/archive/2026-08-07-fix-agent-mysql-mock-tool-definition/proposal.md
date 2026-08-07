## Why

`AgentPersistenceMySqlIntegrationTest` 在 GitHub Actions 的 MySQL 8.4 环境先因测试替身缺少工具定义而无法创建 Spring ApplicationContext；修复后又暴露 V009 只允许 `tool.result`、而当前公开 SSE 使用 `tool.complete/tool.error` 的存储不兼容。两处问题都会阻断 `dev` 的 MySQL 集成测试。

## What Changes

- 为 `RankMoviePlanExecutionAdapter` 的 MySQL 测试 mock 同时配置 `targetName()` 和 `definition()`。
- 在每次 `Mockito.reset(...)` 后恢复上述两项固定行为。
- 将监督器测试计划夹具更新为当前完整 `rankMoviePlan` 的必填输入和结果类型。
- 将仍依赖旧固定推荐服务的 B Agent 测试替换为完整推荐结果替身。
- 检查同一测试配置中的其他 `AgentToolExecutor` mock，防止出现同类空工具定义。
- 终态工具事件在数据库中兼容写为 V009 已允许的 `tool.result`，读取时按受控载荷还原为公开的 `tool.complete` 或 `tool.error`；旧 `tool.result` 保持不变。

## Capabilities

### New Capabilities

- `agent-mysql-test-context`: Agent MySQL 集成测试的工具执行器 mock 能在 Spring 上下文初始化期间提供完整工具定义。

### Modified Capabilities

- `agent-post-sse-interaction`: 不修改已执行的 V009，通过持久化映射保证当前工具终态事件可写入 MySQL 且重放后的公开事件类型不变。

## Impact

- 影响 Agent MySQL 测试和 Agent 事件持久化映射。
- 不修改 MySQL 配置、已执行 Flyway、公开 SSE 字段或业务规则。
