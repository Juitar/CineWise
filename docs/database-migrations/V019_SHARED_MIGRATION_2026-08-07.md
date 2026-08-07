# V019 外部排期本地沙箱映射共享库迁移记录

## 基本信息

- 迁移：`V019__create_ticketing_external_showtime_mapping.sql`
- OpenSpec：`external-showtime-sandbox-ticketing-import`
- 领域 Owner：A / ticketing
- 执行人：A
- 执行日期：2026-08-07
- MySQL：8.4.11
- 目标库：`cinewise`
- 迁移账号：`cinewise_migrator`
- Flyway V019 checksum：`1157183546`

## 发布前检查与恢复证据

- 共享库只读预检确认当前版本为 V018，V019 为唯一 Pending，目标表不存在，失败历史数为 0。
- 连接守卫确认数据库为 `cinewise`、账号为 `cinewise_migrator`；未使用日常应用账号 `cinewise_app`。
- 已生成发布前逻辑备份：`data/migration-backups/cinewise-before-v019-20260807-140101-tables-data-triggers.sql`。
- 备份大小：3,420,761 bytes；SHA-256：`9EE36E170C3560B661115FFF03495E6038575A5ABAA24BE3F6738FF1BEB58E56`。
- 未修改日常 `.env`，未持久化开启 `FLYWAY_ENABLED`。

## 共享库发布结果

- 使用一次性受控 Flyway 进程将共享 `cinewise` 从 V018 升级到 V019，成功应用 1 个迁移。
- 发布后 `flyway validate` 成功校验 19 个迁移，Pending 数量为 0。
- 重复 `migrate` 成功且执行 0 个迁移；V019 历史记录成功标志为 1，checksum 为 `1157183546`。
- `ticketing_external_showtime_mapping` 已创建且当前 0 行；未写入业务或演示数据。
- 表为 InnoDB，字符集/排序规则为 `utf8mb4` / `utf8mb4_0900_ai_ci`；主键、唯一键、查询索引和四条 CHECK 均已核验。

## 结论

V019 已成功发布至共享 `cinewise`，并完成备份、Flyway 历史、发布后 validate、重复执行、结构、索引、CHECK、字符集和排序规则核验。V019 自本次成功执行起冻结；后续结构调整必须新增更高版本的前向迁移。实际外部排期导入持久化、并发幂等和业务联调仍需由 A 代码任务完成，不能因空映射表而宣称已导入真实场次。
