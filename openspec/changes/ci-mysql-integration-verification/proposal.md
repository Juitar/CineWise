## Why

后端快速 CI 使用 H2，真实 MySQL 8.4 的迁移、条件更新、行锁、唯一约束和并发交易测试默认由环境变量跳过，PR 合并前无法自动发现数据库方言或事务行为回归。

## What Changes

- 新增独立的 MySQL 8.4 集成工作流，在一次性隔离库上运行场次、建单并发、订单生命周期、支付和退款集成测试。
- 使用 CI 内固定的临时账号和数据库，不连接共享云端 MySQL，不使用生产或团队日常凭据。
- 将场次查询 MySQL 测试从依赖云端既有种子改为由 Flyway 和固定种子自建，增加数据库名称保护门。
- 将交易集成测试的版本断言同步到项目 MySQL 8.4 基线，并统一使用应用业务时钟选择未来场次。
- 保留现有 H2/静态检查快速质量门；本 change 不修改迁移 SQL、业务代码或共享数据库。

## Capabilities

### New Capabilities

- `ci-mysql-integration-verification`: 使用隔离 MySQL 8.4 自动验证迁移兼容、查询和交易并发行为。

### Modified Capabilities

无。

## Impact

- `.github/workflows/`：新增 MySQL 8.4 集成质量门。
- `ShowQueryMySqlIntegrationTest`：改为可重复的隔离库初始化，不再读取云端预置数据。
- 数据库迁移与生产配置不变；CI 数据随 GitHub Runner 销毁。
