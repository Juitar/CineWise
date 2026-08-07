# V019 外部排期本地沙箱映射迁移验证记录

## 基本信息

- 迁移：`V019__create_ticketing_external_showtime_mapping.sql`
- OpenSpec：`external-showtime-sandbox-ticketing-import`
- 领域 Owner：A / ticketing
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-07
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- Flyway V019 checksum：`1157183546`

## 范围与环境守卫

- V018 已在验证库历史中成功执行；本次仅新增 V019，不修改 V001–V018。
- 运行时使用临时迁移目录补齐已验证的 B 侧 V018 文件，避免当前工作树缺少 V018 原文件导致误报缺失迁移。
- 目标数据库和账号精确核对为 `cinewise_migration_check` / `cinewise_migrator`，MySQL 版本为 8.4.11。
- 未连接共享 `cinewise` 库，未修改日常 `.env`，未持久化开启 `FLYWAY_ENABLED`。

## Flyway 验证

- 执行前版本为 V018，V019 是唯一 Pending 迁移；目标表不存在。
- 首次 `migrate` 成功将验证库升级到 V019，执行 1 个迁移。
- `validate` 成功校验 19 个迁移，Pending 数量为 0。
- 重复 `migrate` 成功且执行 0 个迁移。

## 实际结构与约束

- `ticketing_external_showtime_mapping` 为 InnoDB，表排序规则为 `utf8mb4_0900_ai_ci`；字符串列均使用该排序规则。
- 字段包含外部三元键、本地 `show_id`、`import_mode`、`source`、`data_at`、`expires_at` 及 `create_time/update_time`，均与设计可空性一致。
- 外部三元键唯一，本地 `show_id` 唯一；另有影院/Provider 查询索引和主键索引。
- CHECK 实际存在并生效：ID 为正数、文本非空、导入模式受限、`data_at < expires_at`。
- 正常插入成功；重复外部键、非正本地场次 ID、非法导入模式和逆序时效均被拒绝。
- 所有验证数据在事务中回滚，测试行数为 0。

## 结论

V019 已在 A 专用 MySQL 8.4.11 验证库完成首次 migrate、validate、重复 migrate、结构、索引、CHECK、字符集、排序规则及正反约束验证，结果通过。本记录不构成共享库迁移或 Git 提交授权；实际导入持久化和并发幂等代码仍需完成后再做业务集成验证。
