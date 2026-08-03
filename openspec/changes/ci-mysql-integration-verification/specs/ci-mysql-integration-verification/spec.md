## Purpose

在 PR 合并前使用一次性 MySQL 8.4 验证数据库方言、迁移、查询和交易并发行为，避免 H2 通过掩盖真实数据库回归。

## ADDED Requirements

### Requirement: MySQL 集成测试必须使用一次性 MySQL 8.4

系统 SHALL 为数据库集成验证启动 MySQL 8.4，并仅在服务健康后运行真实 MySQL 测试集合。

#### Scenario: MySQL 服务正常启动

- **WHEN** 后端变更触发 MySQL 集成工作流
- **THEN** 系统创建固定名称的一次性数据库和 CI 应用账号
- **AND** 系统开启场次、建单并发、订单生命周期、支付和退款集成测试

#### Scenario: MySQL 服务或测试失败

- **WHEN** MySQL 未通过健康检查、Flyway 失败或任一集成测试失败
- **THEN** MySQL 集成质量门失败
- **AND** 系统不得把 H2 快速验证视为真实 MySQL 已通过

### Requirement: 数据库测试不得连接共享 MySQL

系统 SHALL 使用固定隔离数据库名，并在测试内核验当前数据库；不得读取共享云端地址、团队日常账号或生产 Secret。

#### Scenario: 数据库配置漂移

- **WHEN** 测试实际连接的数据库名不是 `cinewise_ticketing_concurrency_check`
- **THEN** 测试在修改交易夹具前失败
- **AND** 不执行后续并发、支付或退款验证

### Requirement: 场次查询测试必须自行建立基线

系统 SHALL 通过当前 Flyway 迁移和固定种子建立场次查询数据，不得依赖云端预置数据或执行当天手工准备。

#### Scenario: 从空 MySQL 8.4 执行

- **WHEN** 场次查询集成测试连接新建隔离库
- **THEN** Flyway 迁移到当前版本并生成固定演示场次
- **AND** 查询返回来自 MySQL 的权威场次和座位快照

### Requirement: 快速后端质量门保持独立

系统 SHALL 保留现有 H2、单元测试和静态检查工作流，并将 MySQL 集成结果作为独立质量门报告。

#### Scenario: 仅 MySQL 语义回归

- **WHEN** H2 快速验证通过但 MySQL 条件更新、约束或锁行为失败
- **THEN** MySQL 集成质量门单独失败
- **AND** PR 不得被视为数据库集成通过
