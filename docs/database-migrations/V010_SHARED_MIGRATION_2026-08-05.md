# V010 用户画像表共享库迁移记录

## 执行范围

- 目标迁移：`V010__create_profile_tables.sql`
- OpenSpec：`openspec/changes/user-profile-management/`
- 领域 Owner：D；Flyway 发布负责人：A
- 执行日期：2026-08-05
- 数据库版本：MySQL 8.4.11
- SQL SHA-256：`F1ED5D7409352B1D996D2DB979F6BB86EE1DDFC75A6C55864599F57961311E22`
- Flyway V010 checksum：`1185312571`

## 发布前检查与恢复演练

- OpenSpec 严格校验、V010 空 MySQL 验证、重复 migrate、实际结构、字符集/排序规则和正反约束验证均已完成。
- 发布前只读确认共享 `cinewise` 位于 V009：9 条成功历史、0 条失败历史，V009 checksum 为 `-1153317324`；V010 历史及四张目标表均不存在。
- 迁移账号 `cinewise_migrator` 没有 `SHOW EVENTS` 权限。经代码和 V005 发布记录确认，项目调度使用 Spring `@Scheduled`，不使用数据库 Event 或存储过程；因此备份明确排除数据库 Event/例程，覆盖共享库全部表、数据、触发器和 Flyway 历史。
- 已生成逻辑备份 `data/migration-backups/cinewise-before-v010-20260805-111749-tables-data-triggers.sql`；大小为 1,996,068 字节，SHA-256 为 `00FF6E31092C8979AEC80B819E5F808CBFDB28FAADDA183887FC722273A32AE5`。备份目录被 Git 忽略。
- 备份已恢复至全新的临时 MySQL 8.4.11 容器：25 张表、V001--V009 共 9 条成功历史、失败历史为 0、当前 V009 checksum 为 `-1153317324`；V010 历史及四张目标表均不存在。验证容器已删除。

## 共享库发布结果

- 使用一次性受控 Flyway 进程，以 `cinewise_migrator` 将共享 `cinewise` 从 V009 升级到 V010；Flyway 报告成功应用 1 个迁移。
- 同一冻结 JAR 再次执行：成功校验 10 个迁移，并报告 `Schema cinewise is up to date. No migration necessary.`。
- 两次 non-web 进程均在 Flyway 已完成后因现有认证安全壳缺少 Servlet `HttpSecurity` 而退出。该已知退出不影响已经提交的 Flyway 历史、结构或重复执行结果；未启动持续应用、种子或后台任务。

## 发布后结构检查

- 当前最新成功版本为 V010，共 10 条成功历史、0 条失败历史；V010 checksum 为 `1185312571`。
- `user_preference`、`user_profile_tag`、`user_behavior_event`、`profile_write_request` 均已创建，均为 `utf8mb4_0900_ai_ci`。
- 四表索引与唯一键均存在；共有 29 条 CHECK，物理外键为 0。
- 四张新表在发布后均为 0 行；共享库未写入迁移测试数据。

## 结论

V010 已成功发布至共享 `cinewise`，并完成备份恢复演练、一次性迁移、重复执行与发布后结构核验。V010 自本次成功执行起冻结；如需结构调整，必须新增向前 Flyway 迁移。

本记录不代表 V010 SQL 或本记录已提交到 Git。提交前必须先同步远端 `dev`，核对冻结 SQL 哈希未变化后再以 A 的隔离迁移提交处理。
