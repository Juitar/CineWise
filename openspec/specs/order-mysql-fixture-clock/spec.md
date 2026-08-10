# order-mysql-fixture-clock Specification

## Purpose
TBD - created by archiving change order-mysql-fixture-clock. Update Purpose after archive.
## Requirements
### Requirement: MySQL 并发测试使用确定性业务时间

支付和退款 MySQL 并发测试 SHALL 使用与固定演示种子一致的注入式业务 Clock，并以该业务时间筛选可售场次。测试不得依赖数据库 `CURRENT_TIMESTAMP` 或执行当天日期。

#### Scenario: 日期推进后执行测试

- **GIVEN** 固定种子创建的场次日期早于数据库实际日期
- **WHEN** 执行支付或退款 MySQL 并发测试
- **THEN** 测试仍按固定业务 Clock 选中尚未开场的 `ON_SALE` 场次，并验证原有并发语义

### Requirement: 生产行为不受测试夹具影响

该修复 SHALL 只修改测试源文件和 OpenSpec；不得改变生产订单、支付、退款、场次查询、迁移或种子行为。

#### Scenario: 生产模块边界

- **WHEN** 审查本 change 的变更文件
- **THEN** 不包含 `src/main`、Flyway 或其他 Owner 模块的修改

