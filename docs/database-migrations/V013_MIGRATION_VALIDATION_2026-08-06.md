# V013 出行任务影院标识迁移验证记录

## 基本信息

- 迁移：`V013__add_travel_task_cinema_id.sql`
- OpenSpec：`travel-reminder-experience`
- 领域 Owner：D / travel
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-06
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`1068EE8A1784CCDD8B4EDC998AFD920E2B3BD5E955DF209D0E918A830BA4D4BA`
- Flyway V013 checksum：`-551834400`

## 范围与环境守卫

- V013 仅对已发布的 `travel_task` 增加 `cinema_id BIGINT NULL` 及 `chk_travel_task_cinema_id_positive`。
- 不修改 V007，不建立物理外键、索引或默认值，不回填历史数据，不包含种子、账号、密码或业务修数。
- 启动前确认目标数据库精确为 `cinewise_migration_check`、账号精确为 `cinewise_migrator`；日常 `.env` 与共享 `cinewise` 未用于本次验证。
- 验证输入为仓库 V001 至 V012 加冻结的 V013 SQL；不包含 V014 或其他私发草案。

## Flyway 验证

- 执行前 V001 至 V012 均为 Success，V013 为 Pending。
- 首次 `migrate` 成功将验证库从 V012 升级到 V013；V013 安装时间为 `2026-08-06 10:17:43`。
- `flyway validate` 成功校验 13 个迁移。
- 重复 `migrate` 报告 `Schema cinewise_migration_check is up to date. No migration necessary.`。

## 实际结构与约束

- `travel_task.cinema_id` 实际为 `BIGINT NULL`，列位置紧随 `show_id`，无默认值。
- `chk_travel_task_cinema_id_positive` 实际为 `cinema_id IS NULL OR cinema_id > 0`。
- `travel_task` 保持 InnoDB 和 `utf8mb4_0900_ai_ci`。

## 正反约束用例

- 正数 `cinema_id=4001` 可在事务内写入；回滚后测试行数为 0。
- `CANCELLED` 墓碑任务的 `cinema_id=NULL` 可在事务内写入；回滚后测试行数为 0。
- `cinema_id=0` 与 `cinema_id=-1` 均被 MySQL CHECK 拒绝。
- 验证结束后 `travel_task` 总行数为 0。

## 结论

V013 已在 A 专用 MySQL 8.4.11 验证库完成首次 migrate、validate、重复 migrate、列位置、可空性、CHECK、排序规则及正反约束验证，结果通过。该记录不替代共享库发布授权。
