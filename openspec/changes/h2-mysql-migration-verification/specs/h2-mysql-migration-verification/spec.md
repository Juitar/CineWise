## Purpose

在保留 H2 快速反馈的同时，确保所有当前及未来 Flyway 迁移都由一次性 MySQL 8.4 自动验证，不让数据库方言差异阻塞普通测试或漏过真实迁移错误。

## ADDED Requirements

### Requirement: H2 快速测试必须停在最后兼容迁移

系统 SHALL 将 H2 `test` profile 的 Flyway 目标固定为 V009，不要求 H2 解析 V010 及后续 MySQL 专属 DDL。

#### Scenario: 普通后端验证包含 V010

- **WHEN** `backend\\mvnw.cmd verify` 使用 H2 test profile 运行
- **THEN** Flyway 只应用 V001 至 V009
- **AND** 普通单元、上下文和静态检查不因 V010 的 MySQL 专属语法失败

### Requirement: MySQL CI 必须执行当前全部迁移

系统 SHALL 在一次性 MySQL 8.4 空库中以 `latest` 为目标执行当前全部 Flyway 迁移，不受 H2 V009 目标影响。

#### Scenario: 新增后续迁移

- **WHEN** 仓库新增 V011 或更高版本迁移并触发 MySQL CI
- **THEN** 独立迁移库从空库自动执行到该最新版本
- **AND** 无需修改 CI 中的期望版本号或迁移测试类清单

#### Scenario: 最新迁移失败或仍为 pending

- **WHEN** 任一迁移无法在 MySQL 8.4 执行、历史失败或 migrate 后仍有 pending
- **THEN** MySQL 集成质量门失败
- **AND** H2 快速验证通过不得替代该失败结果

### Requirement: MySQL 迁移必须验证重复执行

系统 SHALL 使用同一冻结迁移集合再次执行 migrate，并要求执行数量为 0。

#### Scenario: 迁移集合已是最新

- **WHEN** 首次空库 migrate 成功后立即重复 migrate
- **THEN** Flyway 报告没有待执行迁移
- **AND** 不重复创建表或改写成功历史

### Requirement: CI 数据库必须与共享环境隔离

系统 SHALL 仅使用当前 GitHub Runner 的临时 MySQL 账号和 `cinewise_migration_ci` 数据库；不得读取 `.env`、云端地址或团队日常凭据。

#### Scenario: 数据库或账号配置漂移

- **WHEN** 迁移守卫连接的数据库名不是 `cinewise_migration_ci`，或账号不是 `cinewise_ci`
- **THEN** 测试在执行 Flyway 前失败
- **AND** 不执行任何迁移或业务写入
