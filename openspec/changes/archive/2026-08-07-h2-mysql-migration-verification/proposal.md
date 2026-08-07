## Why

后端快速测试使用 H2 并自动执行 Flyway。V010 已在 MySQL 8.4 验证和发布，但其生成列使用 MySQL `IF(...)` 表达式，H2 2.3 即使开启 MySQL 模式也无法解析，导致普通 `mvn verify` 在业务测试开始前失败。

项目已经有一次性 MySQL 8.4 CI，不需要新增数据库服务；需要明确 H2 与 MySQL 的验证边界，并保证 MySQL CI 对每个后续迁移自动执行空库首次 migrate、重复 migrate 和无 pending 检查。

## What Changes

- H2 `test` profile 的 Flyway 目标固定为 V009，只承担既有快速测试，不再模拟 V010 之后的 MySQL 专属 DDL。
- 现有 MySQL 8.4 工作流显式使用 `latest` 目标，并增加独立空库的通用 Flyway 守卫测试。
- 通用守卫从空库执行当前全部迁移、重复 migrate，并拒绝任何 pending 或失败历史；新增 V011、V012 时无需修改版本断言或 CI 测试清单。
- 更新后端骨架说明，记录 H2 是快速反馈、MySQL 是迁移权威的长期边界。

## Capabilities

### New Capabilities

- `h2-mysql-migration-verification`: 分离 H2 快速测试与 MySQL 真实迁移验证，并自动验证最新 Flyway 基线。

### Modified Capabilities

无。

## Impact

- `backend/src/test/resources/application-test.yml`：H2 Flyway 固定到 V009。
- `backend/src/test/java/.../FlywayLatestMySqlIntegrationTest.java`：新增隔离 MySQL 空库迁移守卫。
- `.github/workflows/backend-mysql-integration.yml`：创建独立迁移库并运行最新迁移守卫。
- `docs/backend-skeleton.md`：记录测试数据库分工。
- 不修改 V001 至 V010，不连接共享库，不改变生产 Flyway 默认关闭策略。
