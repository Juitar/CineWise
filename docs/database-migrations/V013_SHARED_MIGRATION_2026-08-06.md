# V013 出行任务影院标识共享库迁移记录

## 执行范围

- 目标迁移：`V013__add_travel_task_cinema_id.sql`
- OpenSpec：`openspec/changes/travel-reminder-experience/`
- 领域 Owner：D / travel；Flyway 发布负责人：A
- 执行日期：2026-08-06
- 数据库版本：MySQL 8.4.11
- SQL SHA-256：`1068EE8A1784CCDD8B4EDC998AFD920E2B3BD5E955DF209D0E918A830BA4D4BA`
- Flyway V013 checksum：`-551834400`

## 发布前检查

- A 专用验证库已完成首次 migrate、validate、重复 migrate、实际列/约束、排序规则与正反 CHECK 用例验证。
- 发布前只读确认共享 `cinewise` 位于 V012，V013 为 Pending，且 `travel_task.cinema_id` 不存在。
- 使用独立迁移账号 `cinewise_migrator`；未使用日常应用账号 `cinewise_app`，未修改日常 `FLYWAY_ENABLED` 配置。
- 本次执行前未取得独立的发布前备份与恢复演练证据；该事实如实记录，不能作为已完成备份表述。

## 共享库发布结果

- 使用一次性受控 Flyway 进程将共享 `cinewise` 从 V012 升级到 V013；Flyway 成功应用 1 个迁移。
- V013 安装时间为 `2026-08-06 10:22:58`，成功标志为 1，checksum 为 `-551834400`。
- 发布后 `flyway validate` 成功校验 13 个迁移。
- 发布后重复 `migrate` 报告 `Schema cinewise is up to date. No migration necessary.`。

## 发布后结构检查

- `travel_task.cinema_id` 已创建，为 `BIGINT NULL`，位于 `show_id` 后，无默认值。
- `chk_travel_task_cinema_id_positive` 实际为 `cinema_id IS NULL OR cinema_id > 0`。
- `travel_task` 表排序规则保持 `utf8mb4_0900_ai_ci`。
- 本次仅新增兼容列和 CHECK，不写入业务数据。

## 结论

V013 已成功发布至共享 `cinewise`，并完成 Flyway 历史、发布后 validate、重复执行、列定义、CHECK 与排序规则核验。V013 自本次成功执行起冻结；后续结构调整必须新增更高版本的前向 Flyway 迁移。

本记录保留发布前备份/恢复演练证据未独立核验这一流程缺口，不能将其解释为已完成。
