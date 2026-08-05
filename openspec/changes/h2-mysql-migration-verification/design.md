## Context

`Backend Verify` 使用 H2 运行完整单元、上下文和静态检查；`Backend MySQL Integration` 已使用一次性 MySQL 8.4 运行真实数据库测试。V001 至 V009 恰好兼容两种数据库，V010 的 MySQL 生成列语法首次暴露 H2 不是迁移兼容层。

V010 已在共享 MySQL 执行，必须保持 checksum 不变。修改 V010 或新增 V011 都不能修复 H2 在 V010 解析阶段的失败。

## Decisions

### 1. H2 快速基线冻结在 V009

`application-test.yml` 设置 `spring.flyway.target: "009"`。该配置只服务 `test` profile；现有真实 MySQL 集成测试使用 `dev` profile，不继承 H2 数据源或 V009 目标。

H2 继续覆盖不依赖 V010 新表的 Controller、DTO、应用逻辑和快速上下文测试。D 的画像持久化测试以及所有 V010 之后的迁移语义必须使用 MySQL 8.4。

### 2. MySQL CI 显式选择 latest

现有 MySQL workflow 设置 `SPRING_FLYWAY_TARGET=latest`，明确其职责是不受 H2 V009 边界影响地执行当前全部迁移。CI 仍只使用本 Runner 的临时账号和数据库。

### 3. 独立空库自动验证当前全部迁移

工作流新增 `cinewise_migration_ci`，与票务业务测试库、Agent 测试库隔离。`FlywayLatestMySqlIntegrationTest` 仅在 `CINEWISE_MYSQL_MIGRATION_IT=true` 时运行，并强制校验：

- 数据库名必须是 `cinewise_migration_ci`，账号必须是 `cinewise_ci`；
- 数据库版本必须是 MySQL 8.4；
- 首次 `migrate()` 后不存在 pending 或失败迁移；
- 同一迁移集合重复 `migrate()` 执行数量为 0。

测试不硬编码 V010，因此后续添加 V011、V012 时会自动纳入空库迁移验证，无需修改 workflow 清单。

### 4. 迁移 SQL 仍以 MySQL 为唯一权威

H2 不承担 MySQL DDL 兼容验证。A 的迁移流程仍包括静态审查、专用 MySQL 8.4 验证库、共享发布前备份恢复和受控发布；CI 一次性 MySQL 是 PR 回归门，不替代共享发布授权。

## Risks / Trade-offs

- H2 不会创建 V010 之后的表；错误地在 H2 上新增画像 Repository 测试会因缺表失败。此类测试必须进入 MySQL 集成集合。
- MySQL Job 增加一个空数据库和一次 Spring/Flyway 测试，预计增加数秒到数十秒，但不增加云资源或共享库风险。
- 若未来团队完全迁移到 Testcontainers MySQL，可删除 V009 H2 边界；在此之前不得逐版本复制 H2 专用迁移。

## Rollback Plan

回退 H2 target、通用 MySQL 守卫测试、workflow 新步骤和本文档说明即可；不涉及生产配置、共享库或任何历史迁移。
